package com.quoteflow.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.action.dto.ActionConfirmResponse;
import com.quoteflow.ai.action.dto.ActionProposalSummaryDto;
import com.quoteflow.ai.tool.AiToolRegistry;
import com.quoteflow.identity.TenantRole;
import com.quoteflow.security.AuthenticatedUser;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"quoteflow.ai.actions.enabled=true",
		"quoteflow.ai.actions.approval-ttl=10m",
		"quoteflow.email.provider=FAKE",
		"quoteflow.email.worker-enabled=false"
})
class PaymentReminderSendIntegrationTest extends PostgresIntegrationTest {

	@Autowired WebApplicationContext webApplicationContext;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired AiActionApprovalService approvalService;
	@Autowired AiActionProposalRepository proposalRepository;
	@Autowired AiToolRegistry toolRegistry;
	@Autowired SubscriptionService subscriptionService;
	@Autowired EntityManager entityManager;
	@Autowired com.quoteflow.notification.email.provider.FakeEmailProvider fakeEmailProvider;
	@Autowired com.quoteflow.notification.email.delivery.NotificationDeliveryService deliveryService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
		jdbcTemplate.update("DELETE FROM notifications");
		jdbcTemplate.update("DELETE FROM ai_action_proposals");
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
	void paymentReminderSendToolRegistered() {
		assertThat(toolRegistry.allowlistedNames()).contains("payment_reminder_send");
		assertThat(toolRegistry.allowlistedNames()).doesNotContain(
				"email_send", "email_send_anything", "confirm_action", "reminder_send");
	}

	@Test
	void prepareDoesNotEnqueueAndConfirmQueuesOnce() throws Exception {
		Fixture fx = fixture("send+" + UUID.randomUUID() + "@example.com");
		long before = count("notifications");
		ActionProposalSummaryDto proposal = approvalService.preparePaymentReminderSend(
				fx.user, fx.invoiceId, "Payment reminder", "Please pay the balance when convenient.");
		assertThat(count("notifications")).isEqualTo(before);
		assertThat(proposal.actionType()).isEqualTo(AiActionType.PAYMENT_REMINDER_SEND);
		assertThat(proposal.confirmButtonLabel()).isEqualTo("Approve & Send Reminder");

		MvcResult detail = mockMvc.perform(get("/api/v1/ai/actions/" + proposal.proposalId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payload.recipientEmail").value("buyer@example.com"))
				.andExpect(jsonPath("$.preview.sendsEmail").value(true))
				.andReturn();
		JsonNode body = objectMapper.readTree(detail.getResponse().getContentAsString());
		assertThat(body.path("payload").path("outstandingAmount").decimalValue())
				.isEqualByComparingTo("1000.00");

		ActionConfirmResponse confirmed = approvalService.confirm(fx.user, proposal.proposalId());
		assertThat(confirmed.status()).isEqualTo("EXECUTED");
		assertThat(confirmed.resultReferenceType()).isEqualTo("NOTIFICATION");
		assertThat(count("notifications")).isEqualTo(before + 1);

		deliveryService.deliverClaimedOrPending(confirmed.resultReferenceId());
		assertThat(fakeEmailProvider.sendCalls()).isEqualTo(1);
	}

	@Test
	void confirmDoesNotNeedAiProvider() throws Exception {
		Fixture fx = fixture("offline+" + UUID.randomUUID() + "@example.com");
		var proposal = approvalService.preparePaymentReminderSend(
				fx.user, fx.invoiceId, "Reminder", "Please pay.");
		ActionConfirmResponse confirmed = approvalService.confirm(fx.user, proposal.proposalId());
		assertThat(confirmed.resultReferenceType()).isEqualTo("NOTIFICATION");
	}

	@Test
	void paidInvoiceBlocksPrepare() throws Exception {
		Fixture fx = fixture("paid-prep+" + UUID.randomUUID() + "@example.com");
		payFull(fx.token, fx.invoiceId, "1000.00");
		try {
			approvalService.preparePaymentReminderSend(fx.user, fx.invoiceId, "Reminder", "Please pay.");
			org.junit.jupiter.api.Assertions.fail("expected paid prepare to fail");
		} catch (com.quoteflow.common.api.DomainApiException ex) {
			assertThat(ex.getCode()).isEqualTo("INVOICE_NOT_OUTSTANDING");
		}
		assertThat(count("notifications")).isZero();
	}

	@Test
	void paidRaceBlocksConfirmAndDoesNotEnqueue() throws Exception {
		Fixture fx = fixture("paid-race+" + UUID.randomUUID() + "@example.com");
		var proposal = approvalService.preparePaymentReminderSend(
				fx.user, fx.invoiceId, "Reminder", "Please pay.");
		payFull(fx.token, fx.invoiceId, "1000.00");
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVOICE_NOT_OUTSTANDING"));
		assertThat(count("notifications")).isZero();
		entityManager.clear();
		assertThat(proposalRepository.findById(proposal.proposalId()).orElseThrow().getStatus())
				.isEqualTo(AiActionProposalStatus.FAILED);
	}

	@Test
	void balanceChangeBlocksConfirm() throws Exception {
		Fixture fx = fixture("bal+" + UUID.randomUUID() + "@example.com", "5000.00");
		var proposal = approvalService.preparePaymentReminderSend(
				fx.user, fx.invoiceId, "Reminder", "Balance due.");
		payPartial(fx.token, fx.invoiceId, "2000.00");
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_ACTION_STALE_BALANCE"));
		assertThat(count("notifications")).isZero();
	}

	@Test
	void recipientChangeBlocksConfirm() throws Exception {
		Fixture fx = fixture("rcp+" + UUID.randomUUID() + "@example.com");
		var proposal = approvalService.preparePaymentReminderSend(
				fx.user, fx.invoiceId, "Reminder", "Please pay.");
		jdbcTemplate.update("UPDATE invoices SET customer_email = ? WHERE id = CAST(? AS uuid)",
				"newbuyer@example.com", fx.invoiceId.toString());
		entityManager.clear();
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_ACTION_STALE_RECIPIENT"));
		assertThat(count("notifications")).isZero();
	}

	@Test
	void missingRecipientBlocksPrepare() throws Exception {
		String token = register("norecip+" + UUID.randomUUID() + "@example.com");
		AuthenticatedUser user = principal(token);
		subscriptionService.forcePlanForTests(user.getBusinessId(), PlanId.PRO);
		String customerId = createCustomer(token, "No Email", null);
		UUID invoiceId = createSentInvoice(token, customerId, "500.00");
		try {
			approvalService.preparePaymentReminderSend(user, invoiceId, "Reminder", "Please pay.");
			org.junit.jupiter.api.Assertions.fail("expected missing recipient to fail");
		} catch (com.quoteflow.common.api.DomainApiException ex) {
			assertThat(ex.getCode()).isEqualTo("RECIPIENT_EMAIL_MISSING");
		}
	}

	@Test
	void unsupportedClaimRejected() throws Exception {
		Fixture fx = fixture("claim+" + UUID.randomUUID() + "@example.com");
		try {
			approvalService.preparePaymentReminderSend(
					fx.user, fx.invoiceId, "Urgent", "Pay or legal action will follow with a 20% penalty.");
			org.junit.jupiter.api.Assertions.fail("expected unsupported claim");
		} catch (com.quoteflow.common.api.DomainApiException ex) {
			assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_REMINDER_CLAIM");
		}
	}

	@Test
	void freePlanCannotPrepareSend() throws Exception {
		String token = register("free+" + UUID.randomUUID() + "@example.com");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Cust", "c@example.com");
		UUID invoiceId = createSentInvoice(token, customerId, "100.00");
		try {
			approvalService.preparePaymentReminderSend(user, invoiceId, "Reminder", "Please pay.");
			org.junit.jupiter.api.Assertions.fail("expected entitlement denial");
		} catch (com.quoteflow.common.api.DomainApiException ex) {
			assertThat(ex.getCode()).isEqualTo("FEATURE_NOT_AVAILABLE");
		}
	}

	@Test
	void concurrentConfirmEnqueuesExactlyOne() throws Exception {
		Fixture fx = fixture("race+" + UUID.randomUUID() + "@example.com");
		var proposal = approvalService.preparePaymentReminderSend(
				fx.user, fx.invoiceId, "Reminder", "Please pay.");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		Future<?> f1 = pool.submit(() -> {
			await(start);
			try {
				approvalService.confirm(fx.user, proposal.proposalId());
				successes.incrementAndGet();
			} catch (Exception ex) {
				conflicts.incrementAndGet();
			}
		});
		Future<?> f2 = pool.submit(() -> {
			await(start);
			try {
				approvalService.confirm(fx.user, proposal.proposalId());
				successes.incrementAndGet();
			} catch (Exception ex) {
				conflicts.incrementAndGet();
			}
		});
		start.countDown();
		f1.get();
		f2.get();
		pool.shutdown();
		assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
		assertThat(successes.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(1);
		assertThat(count("notifications")).isEqualTo(1);
	}

	@Test
	void headerInjectionStrippedFromSubject() throws Exception {
		Fixture fx = fixture("hdr+" + UUID.randomUUID() + "@example.com");
		var proposal = approvalService.preparePaymentReminderSend(
				fx.user,
				fx.invoiceId,
				"Hello\r\nBcc: attacker@evil.com",
				"Please pay the outstanding balance.");
		JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/ai/actions/" + proposal.proposalId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		assertThat(detail.path("payload").path("subject").asText()).doesNotContain("\n").doesNotContain("\r");
		ActionConfirmResponse confirmed = approvalService.confirm(fx.user, proposal.proposalId());
		deliveryService.deliverClaimedOrPending(confirmed.resultReferenceId());
		assertThat(fakeEmailProvider.sentMessages().getFirst().subject()).doesNotContain("Bcc:");
	}

	@Test
	void crossTenantBlocked() throws Exception {
		Fixture fx = fixture("a+" + UUID.randomUUID() + "@example.com");
		var proposal = approvalService.preparePaymentReminderSend(
				fx.user, fx.invoiceId, "Reminder", "Please pay.");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com");
		mockMvc.perform(get("/api/v1/ai/actions/" + proposal.proposalId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(status().isNotFound());
		assertThat(count("notifications")).isZero();
	}

	@Test
	void manualPrepareEndpointWorks() throws Exception {
		Fixture fx = fixture("manual+" + UUID.randomUUID() + "@example.com");
		mockMvc.perform(post("/api/v1/ai/actions/payment-reminders/prepare")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"invoiceId", fx.invoiceId.toString(),
								"subject", "Friendly reminder",
								"bodyPlainText", "Please arrange payment soon."))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.actionType").value("PAYMENT_REMINDER_SEND"))
				.andExpect(jsonPath("$.status").value("PENDING"));
		assertThat(count("notifications")).isZero();
	}

	private Fixture fixture(String email) throws Exception {
		return fixture(email, "1000.00");
	}

	private Fixture fixture(String email, String amount) throws Exception {
		String token = register(email);
		AuthenticatedUser user = principal(token);
		subscriptionService.forcePlanForTests(user.getBusinessId(), PlanId.PRO);
		String customerId = createCustomer(token, "Raj Electrical", "buyer@example.com");
		UUID invoiceId = createSentInvoice(token, customerId, amount);
		return new Fixture(token, user, invoiceId);
	}

	private record Fixture(String token, AuthenticatedUser user, UUID invoiceId) {
	}

	private long count(String table) {
		Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
		return n == null ? 0 : n;
	}

	private AuthenticatedUser principal(String token) {
		try {
			JsonNode me = objectMapper.readTree(mockMvc.perform(get("/api/v1/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString());
			return new AuthenticatedUser(
					UUID.fromString(me.get("userId").asText()),
					UUID.fromString(me.get("businessId").asText()),
					TenantRole.valueOf(me.get("tenantRole").asText()),
					me.get("email").asText());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Rem Co",
								"firstName", "Test",
								"lastName", "User",
								"email", email,
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "INR"))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private String createCustomer(String token, String name, String email) throws Exception {
		Map<String, Object> body = new HashMap<>();
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

	private UUID createSentInvoice(String token, String customerId, String unitPrice) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("customerId", customerId);
		body.put("issueDate", LocalDate.now().toString());
		body.put("currency", "INR");
		body.put("discountType", "NONE");
		body.put("discountValue", 0);
		body.put("taxRate", 0);
		body.put("items", List.of(Map.of(
				"description", "Service",
				"quantity", 1,
				"unitPrice", new BigDecimal(unitPrice))));
		JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		JsonNode sent = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + created.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		return UUID.fromString(sent.get("id").asText());
	}

	private void payFull(String token, UUID invoiceId, String amount) throws Exception {
		payPartial(token, invoiceId, amount);
	}

	private void payPartial(String token, UUID invoiceId, String amount) throws Exception {
		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"amount", new BigDecimal(amount),
								"paymentMethod", "CASH"))))
				.andExpect(status().isCreated());
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
