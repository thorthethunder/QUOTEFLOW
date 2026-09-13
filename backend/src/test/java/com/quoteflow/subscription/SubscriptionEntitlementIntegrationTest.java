package com.quoteflow.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionEntitlementIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SubscriptionService subscriptionService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
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
		jdbcTemplate.update("DELETE FROM notifications");
		jdbcTemplate.update("DELETE FROM businesses");
	}

	@Test
	void registerCreatesFreeSubscription() throws Exception {
		String token = register("sub+" + UUID.randomUUID() + "@example.com");
		mockMvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan").value("FREE"))
				.andExpect(jsonPath("$.limits.activeCustomers.limit").value(5))
				.andExpect(jsonPath("$.limits.quotationsThisMonth.limit").value(5))
				.andExpect(jsonPath("$.limits.invoicesThisMonth.limit").value(5))
				.andExpect(jsonPath("$.features.removeQuoteFlowBranding").value(false))
				.andExpect(jsonPath("$.billingCheckoutAvailable").value(false));
	}

	@Test
	void freeCustomerLimitAndConcurrency() throws Exception {
		String token = register("cust+" + UUID.randomUUID() + "@example.com");
		for (int i = 1; i <= 4; i++) {
			createCustomer(token, "C" + i);
		}

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger created = new AtomicInteger();
		AtomicInteger denied = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			final int n = i;
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				MvcResult result = mockMvc.perform(post("/api/v1/customers")
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(Map.of("displayName", "Race" + n))))
						.andReturn();
				int statusCode = result.getResponse().getStatus();
				if (statusCode == 201) {
					created.incrementAndGet();
				} else if (statusCode == 403) {
					denied.incrementAndGet();
					assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
							.isEqualTo("PLAN_LIMIT_REACHED");
				} else {
					throw new IllegalStateException("Unexpected " + statusCode);
				}
				return null;
			}));
		}
		start.countDown();
		for (Future<?> f : futures) {
			f.get(30, TimeUnit.SECONDS);
		}
		pool.shutdownNow();
		assertThat(created.get()).isEqualTo(1);
		assertThat(denied.get()).isEqualTo(1);

		mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", "Sixth"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
				.andExpect(jsonPath("$.details.feature").value("CUSTOMERS"))
				.andExpect(jsonPath("$.details.limit").value(5));
	}

	@Test
	void freeQuotationAndInvoiceMonthlyLimitsWithConcurrency() throws Exception {
		String token = register("doc+" + UUID.randomUUID() + "@example.com");
		String customerId = createCustomer(token, "Cust");
		for (int i = 0; i < 4; i++) {
			createQuotation(token, customerId);
		}
		raceCreates(2, () -> createQuotationRaw(token, customerId));
		assertThat(countQuotations(token)).isEqualTo(5);
		MvcResult sixthQ = createQuotationRaw(token, customerId);
		assertThat(sixthQ.getResponse().getStatus()).isEqualTo(403);

		for (int i = 0; i < 4; i++) {
			createInvoice(token, customerId, false);
		}
		raceCreates(2, () -> createInvoiceRaw(token, customerId));
		assertThat(countInvoices(token)).isEqualTo(5);
		assertThat(createInvoiceRaw(token, customerId).getResponse().getStatus()).isEqualTo(403);
	}

	@Test
	void conversionAndStandaloneShareInvoiceQuota() throws Exception {
		String token = register("conv+" + UUID.randomUUID() + "@example.com");
		String customerId = createCustomer(token, "Cust");
		for (int i = 0; i < 4; i++) {
			createInvoice(token, customerId, false);
		}
		JsonNode quote = createQuotation(token, customerId);
		sendQuotation(token, quote.get("id").asText());

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger created = new AtomicInteger();
		AtomicInteger denied = new AtomicInteger();
		futuresAwait(pool, start, created, denied,
				() -> createInvoiceRaw(token, customerId),
				() -> mockMvc.perform(post("/api/v1/quotations/" + quote.get("id").asText() + "/convert-to-invoice")
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{}"))
						.andReturn());
		assertThat(created.get()).isEqualTo(1);
		assertThat(denied.get()).isEqualTo(1);
		assertThat(countInvoices(token)).isEqualTo(5);
	}

	@Test
	void paymentAndReceiptStillWorkWhenQuotasExhausted() throws Exception {
		String token = register("pay+" + UUID.randomUUID() + "@example.com");
		String customerId = createCustomer(token, "Cust");
		JsonNode invoice = createInvoice(token, customerId, true);
		for (int i = 0; i < 4; i++) {
			createCustomer(token, "Extra" + i);
		}
		for (int i = 0; i < 5; i++) {
			createQuotation(token, customerId);
		}
		for (int i = 0; i < 4; i++) {
			createInvoice(token, customerId, false);
		}
		// quotas exhausted for creates; payment must still succeed
		mockMvc.perform(post("/api/v1/invoices/" + invoice.get("id").asText() + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"amount", new BigDecimal("10.00"),
								"paymentMethod", "CASH"))))
				.andExpect(status().isCreated());
		JsonNode payments = objectMapper.readTree(mockMvc.perform(get("/api/v1/invoices/" + invoice.get("id").asText() + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		String paymentId = payments.get("payments").get(0).get("id").asText();
		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/receipt.pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void proAllowsSixthAndDowngradeBlocksNewCreates() throws Exception {
		String token = register("pro+" + UUID.randomUUID() + "@example.com");
		UUID businessId = businessIdForToken(token);
		subscriptionService.forcePlanForTests(businessId, PlanId.PRO);
		String customerId = null;
		for (int i = 0; i < 6; i++) {
			customerId = createCustomer(token, "P" + i);
		}
		assertThat(customerId).isNotNull();
		for (int i = 0; i < 6; i++) {
			createQuotation(token, customerId);
			createInvoice(token, customerId, false);
		}

		subscriptionService.forcePlanForTests(businessId, PlanId.FREE);
		mockMvc.perform(get("/api/v1/customers/" + customerId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", "Blocked"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
	}

	@Test
	void crossTenantEntitlementIsolation() throws Exception {
		String tokenA = register("a+" + UUID.randomUUID() + "@example.com");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com");
		UUID businessB = businessIdForToken(tokenB);
		subscriptionService.forcePlanForTests(businessB, PlanId.PRO);

		JsonNode a = objectMapper.readTree(mockMvc.perform(get("/api/v1/subscription")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		assertThat(a.get("plan").asText()).isEqualTo("FREE");
		assertThat(a.get("limits").get("activeCustomers").get("limit").asInt()).isEqualTo(5);
	}

	@Test
	void ignoresPlanMassAssignmentOnCustomerCreate() throws Exception {
		String token = register("mass+" + UUID.randomUUID() + "@example.com");
		Map<String, Object> body = new HashMap<>();
		body.put("displayName", "X");
		body.put("plan", "PRO");
		body.put("businessId", UUID.randomUUID().toString());
		mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.plan").value("FREE"));
	}

	@Test
	void pdfBrandingSnapshotSurvivesUpgrade() throws Exception {
		String token = register("pdf+" + UUID.randomUUID() + "@example.com");
		String customerId = createCustomer(token, "Cust");
		JsonNode quote = createQuotation(token, customerId);
		sendQuotation(token, quote.get("id").asText());
		byte[] freePdf = mockMvc.perform(get("/api/v1/quotations/" + quote.get("id").asText() + "/pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(extractPdfText(freePdf)).contains("Generated with QuoteFlow");

		subscriptionService.forcePlanForTests(businessIdForToken(token), PlanId.PRO);
		byte[] afterUpgrade = mockMvc.perform(get("/api/v1/quotations/" + quote.get("id").asText() + "/pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(extractPdfText(afterUpgrade)).contains("Generated with QuoteFlow");

		JsonNode proQuote = createQuotation(token, customerId);
		sendQuotation(token, proQuote.get("id").asText());
		byte[] proPdf = mockMvc.perform(get("/api/v1/quotations/" + proQuote.get("id").asText() + "/pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(extractPdfText(proPdf)).doesNotContain("Generated with QuoteFlow");
	}

	@Test
	void timezoneMonthBoundaryUsesBusinessTimezone() throws Exception {
		String token = register("tz+" + UUID.randomUUID() + "@example.com");
		String customerId = createCustomer(token, "Cust");
		JsonNode quote = createQuotation(token, customerId);
		UUID businessId = businessIdForToken(token);
		ZoneId zone = ZoneId.of("Asia/Kolkata");
		YearMonth previous = YearMonth.now(zone).minusMonths(1);
		Instant midPrevious = previous.atDay(15).atStartOfDay(zone).toInstant();
		jdbcTemplate.update("UPDATE quotations SET created_at = ? WHERE id = ?::uuid",
				java.sql.Timestamp.from(midPrevious), UUID.fromString(quote.get("id").asText()));

		mockMvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.limits.quotationsThisMonth.used").value(0));

		createQuotation(token, customerId);
		mockMvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.limits.quotationsThisMonth.used").value(1));
	}

	private void raceCreates(int threads, ThrowingSupplier<MvcResult> action) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger created = new AtomicInteger();
		AtomicInteger denied = new AtomicInteger();
		List<ThrowingSupplier<MvcResult>> actions = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			actions.add(action);
		}
		futuresAwait(pool, start, created, denied, actions.toArray(ThrowingSupplier[]::new));
		assertThat(created.get() + denied.get()).isEqualTo(threads);
		assertThat(created.get()).isEqualTo(1);
		assertThat(denied.get()).isEqualTo(1);
	}

	@SafeVarargs
	private final void futuresAwait(
			ExecutorService pool,
			CountDownLatch start,
			AtomicInteger created,
			AtomicInteger denied,
			ThrowingSupplier<MvcResult>... actions) throws Exception {
		List<Future<?>> futures = new ArrayList<>();
		for (ThrowingSupplier<MvcResult> action : actions) {
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				MvcResult result = action.get();
				int statusCode = result.getResponse().getStatus();
				if (statusCode == 201 || statusCode == 200) {
					created.incrementAndGet();
				} else if (statusCode == 403) {
					denied.incrementAndGet();
					assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
							.isEqualTo("PLAN_LIMIT_REACHED");
				} else {
					throw new IllegalStateException("Unexpected " + statusCode + " " + result.getResponse().getContentAsString());
				}
				return null;
			}));
		}
		start.countDown();
		for (Future<?> f : futures) {
			f.get(30, TimeUnit.SECONDS);
		}
		pool.shutdownNow();
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T> {
		T get() throws Exception;
	}

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Plan Co",
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

	private UUID businessIdForToken(String token) throws Exception {
		JsonNode me = objectMapper.readTree(mockMvc.perform(get("/api/v1/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		return UUID.fromString(me.get("businessId").asText());
	}

	private String createCustomer(String token, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", name))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private JsonNode createQuotation(String token, String customerId) throws Exception {
		MvcResult result = createQuotationRaw(token, customerId);
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private MvcResult createQuotationRaw(String token, String customerId) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("customerId", customerId);
		body.put("issueDate", LocalDate.now().toString());
		body.put("discountType", "NONE");
		body.put("discountValue", 0);
		body.put("taxRate", 0);
		body.put("items", List.of(Map.of("description", "Q", "quantity", 1, "unitPrice", 10)));
		return mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andReturn();
	}

	private void sendQuotation(String token, String quotationId) throws Exception {
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
	}

	private JsonNode createInvoice(String token, String customerId, boolean send) throws Exception {
		MvcResult result = createInvoiceRaw(token, customerId);
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
		if (!send) {
			return created;
		}
		return objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + created.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private MvcResult createInvoiceRaw(String token, String customerId) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("customerId", customerId);
		body.put("issueDate", LocalDate.now().toString());
		body.put("discountType", "NONE");
		body.put("discountValue", 0);
		body.put("taxRate", 0);
		body.put("items", List.of(Map.of("description", "I", "quantity", 1, "unitPrice", 10)));
		return mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andReturn();
	}

	private long countQuotations(String token) throws Exception {
		return objectMapper.readTree(mockMvc.perform(get("/api/v1/subscription")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andReturn().getResponse().getContentAsString())
				.get("limits").get("quotationsThisMonth").get("used").asLong();
	}

	private long countInvoices(String token) throws Exception {
		return objectMapper.readTree(mockMvc.perform(get("/api/v1/subscription")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andReturn().getResponse().getContentAsString())
				.get("limits").get("invoicesThisMonth").get("used").asLong();
	}

	private static String extractPdfText(byte[] pdf) throws Exception {
		try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(document);
		}
	}
}
