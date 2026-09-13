package com.quoteflow.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.notification.email.provider.FakeEmailProvider;
import com.quoteflow.notification.email.template.HtmlEscaper;
import com.quoteflow.subscription.PlanId;
import com.quoteflow.subscription.SubscriptionService;
import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class NotificationIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SubscriptionService subscriptionService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private com.quoteflow.notification.email.delivery.NotificationDeliveryService deliveryService;

	@Autowired
	private FakeEmailProvider fakeEmailProvider;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
		jdbcTemplate.update("DELETE FROM notifications");
		jdbcTemplate.update("DELETE FROM billing_transactions");
		jdbcTemplate.update("DELETE FROM billing_webhook_events");
		jdbcTemplate.update("DELETE FROM payments");
		jdbcTemplate.update("DELETE FROM invoice_items");
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM quotation_items");
		jdbcTemplate.update("DELETE FROM quotations");
		jdbcTemplate.update("DELETE FROM document_sequences");
		jdbcTemplate.update("DELETE FROM customers");
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM app_users");
		jdbcTemplate.update("DELETE FROM subscriptions");
		jdbcTemplate.update("DELETE FROM businesses");
		fakeEmailProvider.reset();
	}

	@Test
	void freePlanCannotSendEmail() throws Exception {
		String token = register("free+" + UUID.randomUUID() + "@example.com");
		String customerId = createCustomer(token, "Cust", "cust@example.com");
		String quotationId = createQuotation(token, customerId);

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
	}

	@Test
	void proSendEmailMarksSentAndDeliversWithEscapedHtml() throws Exception {
		String token = register("pro+" + UUID.randomUUID() + "@example.com");
		UUID businessId = businessIdForToken(token);
		subscriptionService.forcePlanForTests(businessId, PlanId.PRO);

		String customerId = createCustomer(token, "<script>alert(1)</script>", "buyer@example.com");
		String quotationId = createQuotation(token, customerId);

		MvcResult result = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"message", "Thanks <b>pal</b>"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("QUOTATION_EMAIL"))
				.andExpect(jsonPath("$.status").value("SENT"))
				.andExpect(jsonPath("$.recipientMasked").value("b***@example.com"))
				.andReturn();

		mockMvc.perform(get("/api/v1/quotations/" + quotationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"));

		assertThat(fakeEmailProvider.sendCalls()).isEqualTo(1);
		assertThat(fakeEmailProvider.sentMessages()).hasSize(1);
		String html = fakeEmailProvider.sentMessages().getFirst().htmlBody();
		assertThat(html).doesNotContain("<script>");
		assertThat(html).contains(HtmlEscaper.escape("<script>alert(1)</script>"));
		assertThat(html).contains(HtmlEscaper.escape("Thanks <b>pal</b>"));
		assertThat(fakeEmailProvider.sentMessages().getFirst().attachments()).isNotEmpty();

		String notificationId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(get("/api/v1/notifications/" + notificationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"));
	}

	@Test
	void missingCustomerEmailRejected() throws Exception {
		String token = register("noemail+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		String customerId = createCustomer(token, "No Email", null);
		String quotationId = createQuotation(token, customerId);

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RECIPIENT_EMAIL_MISSING"));
	}

	@Test
	void cancelledQuotationCannotEmail() throws Exception {
		String token = register("cancel+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isConflict());
	}

	@Test
	void concurrentSendEmailCreatesOneNotificationAndOneProviderSend() throws Exception {
		String token = register("conc+" + UUID.randomUUID() + "@example.com");
		UUID businessId = businessIdForToken(token);
		subscriptionService.forcePlanForTests(businessId, PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		fakeEmailProvider.reset();

		UUID quotationUuid = UUID.fromString(quotationId);
		NotificationService.EnqueueCommand command = new NotificationService.EnqueueCommand(
				businessId,
				NotificationType.QUOTATION_EMAIL,
				"c@example.com",
				"C",
				"Quotation Q-000001 from Notify Co",
				"quotation_email",
				Map.of(
						"businessName", "Notify Co",
						"customerName", "C",
						"documentNumber", "Q-000001",
						"totalDisplay", "₹100.00",
						"issueDate", "2026-09-13",
						"customMessage", "",
						"currency", "INR"),
				NotificationReferenceType.QUOTATION,
				quotationUuid,
				null,
				true,
				"quotation-email:" + quotationId + ":concurrent",
				false);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<UUID> id1 = new AtomicReference<>();
		AtomicReference<UUID> id2 = new AtomicReference<>();
		AtomicReference<Exception> error = new AtomicReference<>();
		Future<?> f1 = pool.submit(() -> {
			try {
				start.await();
				id1.set(notificationService.enqueue(command).getId());
			} catch (Exception ex) {
				error.set(ex);
			}
			return null;
		});
		Future<?> f2 = pool.submit(() -> {
			try {
				start.await();
				id2.set(notificationService.enqueue(command).getId());
			} catch (Exception ex) {
				error.set(ex);
			}
			return null;
		});
		start.countDown();
		f1.get(30, TimeUnit.SECONDS);
		f2.get(30, TimeUnit.SECONDS);
		pool.shutdown();

		assertThat(error.get()).isNull();
		assertThat(id1.get()).isNotNull().isEqualTo(id2.get());
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE reference_id = ?::uuid",
				Integer.class,
				quotationId);
		assertThat(count).isEqualTo(1);

		CountDownLatch deliverStart = new CountDownLatch(1);
		ExecutorService deliverPool = Executors.newFixedThreadPool(2);
		Future<?> d1 = deliverPool.submit(() -> {
			deliverStart.await();
			deliveryService.deliverClaimedOrPending(id1.get());
			return null;
		});
		Future<?> d2 = deliverPool.submit(() -> {
			deliverStart.await();
			deliveryService.deliverClaimedOrPending(id1.get());
			return null;
		});
		deliverStart.countDown();
		d1.get(30, TimeUnit.SECONDS);
		d2.get(30, TimeUnit.SECONDS);
		deliverPool.shutdown();

		assertThat(fakeEmailProvider.sendCalls()).isEqualTo(1);
		mockMvc.perform(get("/api/v1/notifications/" + id1.get())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.status").value("SENT"));
	}

	@Test
	void explicitResendAfterSentCreatesSecondNotification() throws Exception {
		String token = register("resend+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);
		String firstId = sendEmail(token, quotationId);
		int callsAfterFirst = fakeEmailProvider.sendCalls();

		MvcResult second = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("resend", true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"))
				.andReturn();
		String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

		assertThat(secondId).isNotEqualTo(firstId);
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE reference_id = ?::uuid",
				Integer.class,
				quotationId);
		assertThat(count).isEqualTo(2);
		assertThat(fakeEmailProvider.sendCalls()).isEqualTo(callsAfterFirst + 1);

		mockMvc.perform(get("/api/v1/notifications/" + firstId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"));
	}

	@Test
	void failedNotificationCanBeRetriedWithoutExplicitResend() throws Exception {
		String token = register("failrec+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);

		fakeEmailProvider.setMode(FakeEmailProvider.Mode.PERMANENT_FAIL);
		MvcResult failed = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andReturn();
		String notificationId = objectMapper.readTree(failed.getResponse().getContentAsString()).get("id").asText();

		fakeEmailProvider.setMode(FakeEmailProvider.Mode.SUCCESS);
		MvcResult retried = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"))
				.andExpect(jsonPath("$.id").value(notificationId))
				.andReturn();

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE reference_id = ?::uuid",
				Integer.class,
				quotationId);
		assertThat(count).isEqualTo(1);
		assertThat(objectMapper.readTree(retried.getResponse().getContentAsString()).get("id").asText())
				.isEqualTo(notificationId);
	}

	@Test
	void doubleClickDoesNotDuplicateSend() throws Exception {
		String token = register("dbl+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		String id1 = sendEmail(token, quotationId);
		String id2 = sendEmail(token, quotationId);
		assertThat(id1).isEqualTo(id2);
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE reference_id = ?::uuid",
				Integer.class,
				quotationId);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void providerFailureDoesNotUndoSentStatusAndRetries() throws Exception {
		String token = register("retry+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);

		fakeEmailProvider.failTransientTimes(1);
		MvcResult result = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		// First dispatch failed transiently → PENDING for retry
		assertThat(node.get("status").asText()).isIn("PENDING", "SENT");
		mockMvc.perform(get("/api/v1/quotations/" + quotationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.status").value("SENT"));

		UUID notificationId = UUID.fromString(node.get("id").asText());
		jdbcTemplate.update("UPDATE notifications SET next_attempt_at = NOW() - INTERVAL '1 minute' WHERE id = ?", notificationId);
		deliveryService.deliverClaimedOrPending(notificationId);

		mockMvc.perform(get("/api/v1/notifications/" + notificationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.status").value("SENT"));
		assertThat(fakeEmailProvider.sendCalls()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void crossTenantNotificationAccessDenied() throws Exception {
		String tokenA = register("a+" + UUID.randomUUID() + "@example.com");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(tokenA), PlanId.PRO);
		subscriptionService.forcePlanForTests(businessIdForToken(tokenB), PlanId.PRO);
		String customerId = createCustomer(tokenA, "C", "c@example.com");
		String quotationId = createQuotation(tokenA, customerId);
		String notificationId = sendEmail(tokenA, quotationId);

		mockMvc.perform(get("/api/v1/notifications/" + notificationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void invoiceReminderRequiresOutstandingBalance() throws Exception {
		String token = register("inv+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		MvcResult invoiceResult = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/convert-to-invoice")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andReturn();
		String invoiceId = objectMapper.readTree(invoiceResult.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/send-reminder")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("tone", "FRIENDLY"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("INVOICE_REMINDER"))
				.andExpect(jsonPath("$.status").value("SENT"));

		assertThat(fakeEmailProvider.sentMessages().getLast().htmlBody()).contains("outstanding");
	}

	@Test
	void concurrentInvoiceReminderCreatesOneNotificationAndOneProviderSend() throws Exception {
		String token = register("invconc+" + UUID.randomUUID() + "@example.com");
		UUID businessId = businessIdForToken(token);
		subscriptionService.forcePlanForTests(businessId, PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		MvcResult invoiceResult = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/convert-to-invoice")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andReturn();
		String invoiceId = objectMapper.readTree(invoiceResult.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		fakeEmailProvider.reset();

		UUID invoiceUuid = UUID.fromString(invoiceId);
		NotificationService.EnqueueCommand command = new NotificationService.EnqueueCommand(
				businessId,
				NotificationType.INVOICE_REMINDER,
				"c@example.com",
				"C",
				"Payment reminder: INV-000001 from Notify Co",
				"invoice_reminder_standard",
				Map.of(
						"businessName", "Notify Co",
						"customerName", "C",
						"documentNumber", "INV-000001",
						"balanceDisplay", "₹100.00",
						"customMessage", "",
						"currency", "INR"),
				NotificationReferenceType.INVOICE,
				invoiceUuid,
				null,
				false,
				"invoice-reminder:" + invoiceId + ":concurrent",
				false);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<UUID> id1 = new AtomicReference<>();
		AtomicReference<UUID> id2 = new AtomicReference<>();
		AtomicReference<Exception> error = new AtomicReference<>();
		Future<?> f1 = pool.submit(() -> {
			try {
				start.await();
				id1.set(notificationService.enqueue(command).getId());
			} catch (Exception ex) {
				error.set(ex);
			}
			return null;
		});
		Future<?> f2 = pool.submit(() -> {
			try {
				start.await();
				id2.set(notificationService.enqueue(command).getId());
			} catch (Exception ex) {
				error.set(ex);
			}
			return null;
		});
		start.countDown();
		f1.get(30, TimeUnit.SECONDS);
		f2.get(30, TimeUnit.SECONDS);
		pool.shutdown();

		assertThat(error.get()).isNull();
		assertThat(id1.get()).isNotNull().isEqualTo(id2.get());
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE reference_id = ?::uuid AND type = 'INVOICE_REMINDER'",
				Integer.class,
				invoiceId);
		assertThat(count).isEqualTo(1);

		CountDownLatch deliverStart = new CountDownLatch(1);
		ExecutorService deliverPool = Executors.newFixedThreadPool(2);
		Future<?> d1 = deliverPool.submit(() -> {
			deliverStart.await();
			deliveryService.deliverClaimedOrPending(id1.get());
			return null;
		});
		Future<?> d2 = deliverPool.submit(() -> {
			deliverStart.await();
			deliveryService.deliverClaimedOrPending(id1.get());
			return null;
		});
		deliverStart.countDown();
		d1.get(30, TimeUnit.SECONDS);
		d2.get(30, TimeUnit.SECONDS);
		deliverPool.shutdown();

		assertThat(fakeEmailProvider.sendCalls()).isEqualTo(1);
	}

	@Test
	void concurrentClaimSendsOnce() throws Exception {
		String token = register("claim+" + UUID.randomUUID() + "@example.com");
		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);

		// Enqueue without immediate dispatch by inserting via API then resetting provider and re-PENDING
		String notificationId = sendEmail(token, quotationId);
		jdbcTemplate.update(
				"UPDATE notifications SET status='PENDING', attempt_count=0, next_attempt_at=NOW() - INTERVAL '1 minute', sent_at=NULL, provider_message_id=NULL WHERE id = ?::uuid",
				notificationId);
		fakeEmailProvider.reset();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		Future<?> f1 = pool.submit(() -> {
			start.await();
			deliveryService.deliverClaimedOrPending(UUID.fromString(notificationId));
			return null;
		});
		Future<?> f2 = pool.submit(() -> {
			start.await();
			deliveryService.deliverClaimedOrPending(UUID.fromString(notificationId));
			return null;
		});
		start.countDown();
		f1.get(30, TimeUnit.SECONDS);
		f2.get(30, TimeUnit.SECONDS);
		pool.shutdown();

		assertThat(fakeEmailProvider.sendCalls()).isEqualTo(1);
	}

	private String sendEmail(String token, String quotationId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String sendReminder(String token, String invoiceId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/send-reminder")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("tone", "STANDARD"))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String createQuotation(String token, String customerId) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"customerId", customerId,
								"discountType", "NONE",
								"discountValue", 0,
								"taxRate", 0,
								"items", List.of(Map.of(
										"description", "Work",
										"quantity", 1,
										"unitPrice", 100))))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
	}

	@Test
	void staleSendingNotificationIsReclaimedAfterWorkerCrash() throws Exception {
		String token = register("stale+" + UUID.randomUUID() + "@example.com");
		UUID businessId = businessIdForToken(token);
		subscriptionService.forcePlanForTests(businessId, PlanId.PRO);
		String customerId = createCustomer(token, "C", "c@example.com");
		String quotationId = createQuotation(token, customerId);

		Notification notification = notificationService.enqueue(new NotificationService.EnqueueCommand(
				businessId,
				NotificationType.QUOTATION_EMAIL,
				"c@example.com",
				"C",
				"Quote Q-000001 from Notify Co",
				"quotation_email",
				Map.of(
						"businessName", "Notify Co",
						"customerName", "C",
						"documentNumber", "Q-000001",
						"customMessage", "",
						"currency", "INR"),
				NotificationReferenceType.QUOTATION,
				UUID.fromString(quotationId),
				null,
				true,
				"stale-claim:" + quotationId,
				false));

		jdbcTemplate.update("""
				UPDATE notifications
				SET status = 'SENDING',
				    updated_at = NOW() - INTERVAL '15 minutes'
				WHERE id = ?
				""", notification.getId());

		fakeEmailProvider.reset();
		List<UUID> claimed = deliveryService.claimBatch();
		assertThat(claimed).contains(notification.getId());
		deliveryService.deliver(notification.getId());

		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM notifications WHERE id = ?",
				String.class,
				notification.getId());
		assertThat(status).isEqualTo("SENT");
		assertThat(fakeEmailProvider.sentMessages()).hasSize(1);
	}

	private String createCustomer(String token, String name, String email) throws Exception {
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("displayName", name);
		if (email != null) {
			body.put("email", email);
		}
		MvcResult result = mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Notify Co",
								"firstName", "A",
								"lastName", "B",
								"email", email,
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "INR"))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private UUID businessIdForToken(String token) throws Exception {
		MvcResult me = mockMvc.perform(get("/api/v1/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		return UUID.fromString(objectMapper.readTree(me.getResponse().getContentAsString()).get("businessId").asText());
	}
}
