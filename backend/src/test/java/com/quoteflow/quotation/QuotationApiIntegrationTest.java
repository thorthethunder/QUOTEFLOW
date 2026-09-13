package com.quoteflow.quotation;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class QuotationApiIntegrationTest extends PostgresIntegrationTest {

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
	void createListUpdateSendCancelAndIgnoreClientTotals() throws Exception {
		String token = register("q+" + UUID.randomUUID() + "@example.com", "Quote Co");
		String customerId = createCustomer(token, "Ada Customer");

		Map<String, Object> payload = new java.util.HashMap<>();
		payload.put("customerId", customerId);
		payload.put("discountType", "PERCENTAGE");
		payload.put("discountValue", 10);
		payload.put("taxRate", 18);
		payload.put("notes", "Hello");
		payload.put("subtotal", 1);
		payload.put("discountAmount", 0);
		payload.put("taxAmount", 0);
		payload.put("totalAmount", 1);
		payload.put("status", "SENT");
		payload.put("quotationNumber", "HACKED");
		payload.put("businessId", UUID.randomUUID().toString());
		payload.put("items", List.of(
				Map.of("description", "A", "quantity", 2, "unitPrice", 100),
				Map.of("description", "B", "quantity", 1, "unitPrice", 50)));
		String body = objectMapper.writeValueAsString(payload);
		MvcResult created = mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.quotationNumber").value("Q-000001"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.subtotal").value(250.00))
				.andExpect(jsonPath("$.discountAmount").value(25.00))
				.andExpect(jsonPath("$.taxAmount").value(40.50))
				.andExpect(jsonPath("$.totalAmount").value(265.50))
				.andExpect(jsonPath("$.customerDisplayName").value("Ada Customer"))
				.andReturn();

		JsonNode quotation = objectMapper.readTree(created.getResponse().getContentAsString());
		String quotationId = quotation.get("id").asText();
		long version = quotation.get("version").asLong();

		mockMvc.perform(put("/api/v1/customers/" + customerId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"displayName", "Ada Renamed",
								"email", "renamed@example.com"))))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/quotations/" + quotationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerDisplayName").value("Ada Customer"));

		MvcResult updated = mockMvc.perform(put("/api/v1/quotations/" + quotationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"customerId", customerId,
								"issueDate", quotation.get("issueDate").asText(),
								"discountType", "PERCENTAGE",
								"discountValue", 10,
								"taxRate", 18,
								"version", version,
								"items", List.of(
										Map.of("description", "A", "quantity", 2, "unitPrice", 100),
										Map.of("description", "B", "quantity", 1, "unitPrice", 50))))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalAmount").value(265.50))
				.andReturn();
		long version2 = objectMapper.readTree(updated.getResponse().getContentAsString()).get("version").asLong();

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("version", version2))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"));

		mockMvc.perform(put("/api/v1/quotations/" + quotationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"customerId", customerId,
								"issueDate", quotation.get("issueDate").asText(),
								"discountType", "NONE",
								"discountValue", 0,
								"taxRate", 0,
								"version", version2 + 1,
								"items", List.of(Map.of("description", "X", "quantity", 1, "unitPrice", 1))))))
				.andExpect(status().isConflict());

		JsonNode sent = objectMapper.readTree(mockMvc.perform(get("/api/v1/quotations/" + quotationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("version", sent.get("version").asLong()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void tenantIsolationAndArchivedCustomerBlocked() throws Exception {
		String tokenA = register("a+" + UUID.randomUUID() + "@example.com", "Biz A");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com", "Biz B");
		String customerA = createCustomer(tokenA, "Cust A");
		String customerB = createCustomer(tokenB, "Cust B");

		String quotationA = createQuotation(tokenA, customerA).get("id").asText();
		String quotationB = createQuotation(tokenB, customerB).get("id").asText();

		mockMvc.perform(get("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/api/v1/quotations/" + quotationB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(minimalCreateBody(customerB)))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/customers/" + customerA + "/archive")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(minimalCreateBody(customerA)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/quotations/" + quotationA)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk());
	}

	@Test
	void numberingIsTenantScopedAndConcurrencySafe() throws Exception {
		String tokenA = register("na+" + UUID.randomUUID() + "@example.com", "Num A");
		String tokenB = register("nb+" + UUID.randomUUID() + "@example.com", "Num B");
		subscriptionService.forcePlanForTests(businessIdForToken(tokenA), PlanId.PRO);
		subscriptionService.forcePlanForTests(businessIdForToken(tokenB), PlanId.PRO);
		String customerA = createCustomer(tokenA, "Cust A");
		String customerB = createCustomer(tokenB, "Cust B");

		String firstA = createQuotation(tokenA, customerA).get("quotationNumber").asText();
		String secondA = createQuotation(tokenA, customerA).get("quotationNumber").asText();
		String firstB = createQuotation(tokenB, customerB).get("quotationNumber").asText();

		assertThat(firstA).isEqualTo("Q-000001");
		assertThat(secondA).isEqualTo("Q-000002");
		assertThat(firstB).isEqualTo("Q-000001");

		ExecutorService pool = Executors.newFixedThreadPool(8);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> futures = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return createQuotation(tokenA, customerA).get("quotationNumber").asText();
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
	void optimisticLockRejectsStaleUpdate() throws Exception {
		String token = register("ol+" + UUID.randomUUID() + "@example.com", "OL Co");
		String customerId = createCustomer(token, "Cust");
		JsonNode quotation = createQuotation(token, customerId);
		String id = quotation.get("id").asText();
		long version = quotation.get("version").asLong();

		String updateBody = objectMapper.writeValueAsString(Map.of(
				"customerId", customerId,
				"issueDate", quotation.get("issueDate").asText(),
				"discountType", "NONE",
				"discountValue", 0,
				"taxRate", 0,
				"version", version,
				"items", List.of(Map.of("description", "Updated", "quantity", 1, "unitPrice", 10))));

		mockMvc.perform(put("/api/v1/quotations/" + id)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/quotations/" + id)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
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

	private JsonNode createQuotation(String token, String customerId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(minimalCreateBody(customerId)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private String minimalCreateBody(String customerId) throws Exception {
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
