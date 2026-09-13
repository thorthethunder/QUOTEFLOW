package com.quoteflow.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceApiIntegrationTest extends PostgresIntegrationTest {

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
	void standaloneCreateIgnoresClientTotalsAndUsesInvoiceNumbering() throws Exception {
		String token = register("inv+" + UUID.randomUUID() + "@example.com", "Invoice Co");
		String customerId = createCustomer(token, "Ada Customer");

		Map<String, Object> payload = new HashMap<>();
		payload.put("customerId", customerId);
		payload.put("discountType", "PERCENTAGE");
		payload.put("discountValue", 10);
		payload.put("taxRate", 18);
		payload.put("subtotal", 1);
		payload.put("discountAmount", 0);
		payload.put("taxAmount", 0);
		payload.put("totalAmount", 1);
		payload.put("status", "SENT");
		payload.put("invoiceNumber", "HACKED");
		payload.put("businessId", UUID.randomUUID().toString());
		payload.put("items", List.of(
				Map.of("description", "A", "quantity", 2, "unitPrice", 100),
				Map.of("description", "B", "quantity", 1, "unitPrice", 50)));

		mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(payload)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.invoiceNumber").value("INV-000001"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.subtotal").value(250.00))
				.andExpect(jsonPath("$.discountAmount").value(25.00))
				.andExpect(jsonPath("$.taxAmount").value(40.50))
				.andExpect(jsonPath("$.totalAmount").value(265.50))
				.andExpect(jsonPath("$.sourceQuotationId").doesNotExist());
	}

	@Test
	void convertMatchesCanonicalTotalsAndCopiesSnapshots() throws Exception {
		String token = register("conv+" + UUID.randomUUID() + "@example.com", "Original Consulting");
		String customerId = createCustomer(token, "Alice Original");
		JsonNode quotation = createCanonicalQuotation(token, customerId);
		String quotationId = quotation.get("id").asText();

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		jdbcTemplate.update(
				"UPDATE customers SET display_name = ? WHERE id = ?::uuid",
				"Alice Updated",
				UUID.fromString(customerId));
		jdbcTemplate.update(
				"UPDATE businesses SET name = ? WHERE id = (SELECT business_id FROM quotations WHERE id = ?::uuid)",
				"New Consulting",
				UUID.fromString(quotationId));

		MvcResult converted = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/convert-to-invoice")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.invoiceNumber").value("INV-000001"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.subtotal").value(250.00))
				.andExpect(jsonPath("$.discountAmount").value(25.00))
				.andExpect(jsonPath("$.taxAmount").value(40.50))
				.andExpect(jsonPath("$.totalAmount").value(265.50))
				.andExpect(jsonPath("$.customerDisplayName").value("Alice Original"))
				.andExpect(jsonPath("$.businessName").value("Original Consulting"))
				.andExpect(jsonPath("$.sourceQuotationId").value(quotationId))
				.andReturn();

		JsonNode invoice = objectMapper.readTree(converted.getResponse().getContentAsString());
		String invoiceId = invoice.get("id").asText();

		mockMvc.perform(get("/api/v1/quotations/" + quotationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.convertedInvoiceId").value(invoiceId))
				.andExpect(jsonPath("$.convertedInvoiceNumber").value("INV-000001"));

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/convert-to-invoice")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("QUOTATION_ALREADY_INVOICED"))
				.andExpect(jsonPath("$.details.existingInvoiceId").value(invoiceId));
	}

	@Test
	void concurrentConversionCreatesExactlyOneInvoice() throws Exception {
		String token = register("race+" + UUID.randomUUID() + "@example.com", "Race Co");
		String customerId = createCustomer(token, "Cust");
		JsonNode quotation = createCanonicalQuotation(token, customerId);
		String quotationId = quotation.get("id").asText();
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger created = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		List<Future<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				MvcResult result = mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/convert-to-invoice")
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
						.andReturn();
				int status = result.getResponse().getStatus();
				if (status == 201) {
					created.incrementAndGet();
				} else if (status == 409) {
					conflicts.incrementAndGet();
					JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
					assertThat(body.get("code").asText()).isEqualTo("QUOTATION_ALREADY_INVOICED");
				} else {
					throw new IllegalStateException("Unexpected status " + status);
				}
				return status;
			}));
		}
		start.countDown();
		for (Future<Integer> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}
		pool.shutdownNow();

		assertThat(created.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(1);
		Integer invoiceCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM invoices WHERE source_quotation_id = ?::uuid",
				Integer.class,
				UUID.fromString(quotationId));
		assertThat(invoiceCount).isEqualTo(1);
	}

	@Test
	void documentIndependenceAfterConversion() throws Exception {
		String token = register("indep+" + UUID.randomUUID() + "@example.com", "Indep Co");
		String customerId = createCustomer(token, "Cust");
		JsonNode quotation = createCanonicalQuotation(token, customerId);
		String quotationId = quotation.get("id").asText();
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		JsonNode invoice = objectMapper.readTree(mockMvc.perform(
						post("/api/v1/quotations/" + quotationId + "/convert-to-invoice")
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString());

		String invoiceId = invoice.get("id").asText();
		long version = invoice.get("version").asLong();

		Map<String, Object> update = new HashMap<>();
		update.put("customerId", customerId);
		update.put("issueDate", invoice.get("issueDate").asText());
		update.put("discountType", "NONE");
		update.put("discountValue", 0);
		update.put("taxRate", 0);
		update.put("version", version);
		update.put("items", List.of(Map.of("description", "Changed line", "quantity", 1, "unitPrice", 999)));

		mockMvc.perform(put("/api/v1/invoices/" + invoiceId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalAmount").value(999.00));

		mockMvc.perform(get("/api/v1/quotations/" + quotationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalAmount").value(265.50));
	}

	@Test
	void archivedCustomerBlocksStandaloneButAllowsConversion() throws Exception {
		String token = register("arch+" + UUID.randomUUID() + "@example.com", "Arch Co");
		String customerId = createCustomer(token, "Archive Me");
		JsonNode quotation = createCanonicalQuotation(token, customerId);
		String quotationId = quotation.get("id").asText();
		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/customers/" + customerId + "/archive")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(minimalInvoiceBody(customerId)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/convert-to-invoice")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.customerDisplayName").value("Archive Me"));
	}

	@Test
	void lifecycleOptimisticLockAndCrossTenantIsolation() throws Exception {
		String tokenA = register("a+" + UUID.randomUUID() + "@example.com", "Biz A");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com", "Biz B");
		String customerA = createCustomer(tokenA, "Cust A");
		String customerB = createCustomer(tokenB, "Cust B");

		JsonNode invoiceA = createInvoice(tokenA, customerA);
		JsonNode invoiceB = createInvoice(tokenB, customerB);
		String idA = invoiceA.get("id").asText();
		String idB = invoiceB.get("id").asText();
		long version = invoiceA.get("version").asLong();

		mockMvc.perform(get("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/api/v1/invoices/" + idB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/v1/invoices/" + idB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"customerId", customerA,
								"issueDate", invoiceA.get("issueDate").asText(),
								"discountType", "NONE",
								"discountValue", 0,
								"taxRate", 0,
								"version", 0,
								"items", List.of(Map.of("description", "X", "quantity", 1, "unitPrice", 1))))))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/invoices/" + idB + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound());

		String updateBody = objectMapper.writeValueAsString(Map.of(
				"customerId", customerA,
				"issueDate", invoiceA.get("issueDate").asText(),
				"discountType", "NONE",
				"discountValue", 0,
				"taxRate", 0,
				"version", version,
				"items", List.of(Map.of("description", "Updated", "quantity", 1, "unitPrice", 10))));

		mockMvc.perform(put("/api/v1/invoices/" + idA)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/invoices/" + idA)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
				.andExpect(status().isConflict());

		JsonNode fresh = objectMapper.readTree(mockMvc.perform(get("/api/v1/invoices/" + idA)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());

		mockMvc.perform(post("/api/v1/invoices/" + idA + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("version", fresh.get("version").asLong()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"));

		mockMvc.perform(put("/api/v1/invoices/" + idA)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"customerId", customerA,
								"issueDate", fresh.get("issueDate").asText(),
								"discountType", "NONE",
								"discountValue", 0,
								"taxRate", 0,
								"version", fresh.get("version").asLong() + 1,
								"items", List.of(Map.of("description", "Nope", "quantity", 1, "unitPrice", 1))))))
				.andExpect(status().isConflict());

		JsonNode quoteB = createCanonicalQuotation(tokenB, customerB);
		mockMvc.perform(post("/api/v1/quotations/" + quoteB.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/quotations/" + quoteB.get("id").asText() + "/convert-to-invoice")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
	}

	@Test
	void invoiceNumberingIsTenantScopedAndSharedAcrossModes() throws Exception {
		String tokenA = register("numA+" + UUID.randomUUID() + "@example.com", "Num A");
		String tokenB = register("numB+" + UUID.randomUUID() + "@example.com", "Num B");
		subscriptionService.forcePlanForTests(businessIdForToken(tokenA), PlanId.PRO);
		subscriptionService.forcePlanForTests(businessIdForToken(tokenB), PlanId.PRO);
		String customerA = createCustomer(tokenA, "A");
		String customerB = createCustomer(tokenB, "B");

		assertThat(createInvoice(tokenA, customerA).get("invoiceNumber").asText()).isEqualTo("INV-000001");

		JsonNode quotation = createCanonicalQuotation(tokenA, customerA);
		mockMvc.perform(post("/api/v1/quotations/" + quotation.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		JsonNode converted = objectMapper.readTree(mockMvc.perform(
						post("/api/v1/quotations/" + quotation.get("id").asText() + "/convert-to-invoice")
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString());
		assertThat(converted.get("invoiceNumber").asText()).isEqualTo("INV-000002");

		assertThat(createInvoice(tokenB, customerB).get("invoiceNumber").asText()).isEqualTo("INV-000001");

		ExecutorService pool = Executors.newFixedThreadPool(8);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> futures = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return createInvoice(tokenA, customerA).get("invoiceNumber").asText();
			}));
		}
		start.countDown();
		Set<String> numbers = futures.stream().map(f -> {
			try {
				return f.get(20, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).collect(java.util.stream.Collectors.toSet());
		pool.shutdownNow();
		assertThat(numbers).hasSize(8);
	}

	@Test
	void draftQuotationCannotConvert() throws Exception {
		String token = register("draft+" + UUID.randomUUID() + "@example.com", "Draft Co");
		String customerId = createCustomer(token, "Cust");
		JsonNode quotation = createCanonicalQuotation(token, customerId);
		mockMvc.perform(post("/api/v1/quotations/" + quotation.get("id").asText() + "/convert-to-invoice")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isConflict());
	}

	private String register(String email, String businessName) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", businessName,
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

	private JsonNode createCanonicalQuotation(String token, String customerId) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("customerId", customerId);
		payload.put("discountType", "PERCENTAGE");
		payload.put("discountValue", 10);
		payload.put("taxRate", 18);
		payload.put("items", List.of(
				Map.of("description", "A", "quantity", 2, "unitPrice", 100),
				Map.of("description", "B", "quantity", 1, "unitPrice", 50)));
		MvcResult result = mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(payload)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode createInvoice(String token, String customerId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(minimalInvoiceBody(customerId)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private String minimalInvoiceBody(String customerId) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"customerId", customerId,
				"discountType", "NONE",
				"discountValue", 0,
				"taxRate", 0,
				"items", List.of(Map.of(
						"description", "Service",
						"quantity", 1,
						"unitPrice", new BigDecimal("100.00")))));
	}
}
