package com.quoteflow.security;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 14 dedicated cross-tenant isolation regression suite (Business A vs Business B).
 * Expectation: inaccessible tenant objects return 404 (no existence leakage).
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationSecurityIT extends PostgresIntegrationTest {

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
		jdbcTemplate.update("DELETE FROM notifications");
		jdbcTemplate.update("DELETE FROM payments");
		jdbcTemplate.update("DELETE FROM invoice_items");
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM quotation_items");
		jdbcTemplate.update("DELETE FROM quotations");
		jdbcTemplate.update("DELETE FROM document_sequences");
		jdbcTemplate.update("DELETE FROM customers");
		jdbcTemplate.update("DELETE FROM billing_transactions");
		jdbcTemplate.update("DELETE FROM billing_webhook_events");
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM app_users");
		jdbcTemplate.update("DELETE FROM subscriptions");
		jdbcTemplate.update("DELETE FROM businesses");
	}

	@Test
	void businessACannotAccessBusinessBResources() throws Exception {
		String tokenA = register("iso-a+" + UUID.randomUUID() + "@example.com");
		String tokenB = register("iso-b+" + UUID.randomUUID() + "@example.com");
		UUID businessA = businessId(tokenA);
		subscriptionService.forcePlanForTests(businessA, PlanId.PRO);
		subscriptionService.forcePlanForTests(businessId(tokenB), PlanId.PRO);

		String customerB = createCustomer(tokenB, "B Cust", "b-cust@example.com");
		String quotationB = createQuotation(tokenB, customerB);
		mockMvc.perform(post("/api/v1/quotations/" + quotationB + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		MvcResult email = mockMvc.perform(post("/api/v1/quotations/" + quotationB + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn();
		String notificationB = objectMapper.readTree(email.getResponse().getContentAsString()).get("id").asText();

		MvcResult invoiceResult = mockMvc.perform(post("/api/v1/quotations/" + quotationB + "/convert-to-invoice")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isCreated())
				.andReturn();
		String invoiceB = objectMapper.readTree(invoiceResult.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(post("/api/v1/invoices/" + invoiceB + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		MvcResult payment = mockMvc.perform(post("/api/v1/invoices/" + invoiceB + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"amount", "50.00",
								"paymentMethod", "CASH"))))
				.andExpect(status().isCreated())
				.andReturn();
		String paymentB = objectMapper.readTree(payment.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/v1/customers/" + customerB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/customers/" + customerB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", "Hacked"))))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/customers/" + customerB + "/archive")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/quotations/" + quotationB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/quotations/" + quotationB + "/pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/quotations/" + quotationB + "/send-email")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/quotations/" + quotationB + "/notifications")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/invoices/" + invoiceB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/invoices/" + invoiceB + "/send-reminder")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/invoices/" + invoiceB + "/notifications")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/invoices/" + invoiceB + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"amount", 1,
								"paymentMethod", "CASH",
								"paymentDate", "2026-09-13"))))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/payments/" + paymentB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/payments/" + paymentB + "/receipt.pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/payments/" + paymentB + "/void")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/notifications/" + notificationB)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());

		MvcResult list = mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(list.getResponse().getContentAsString()).doesNotContain(customerB);
	}

	private UUID businessId(String token) throws Exception {
		MvcResult me = mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		return UUID.fromString(objectMapper.readTree(me.getResponse().getContentAsString()).get("businessId").asText());
	}

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Biz " + email,
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

	private String createCustomer(String token, String name, String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", name, "email", email))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String createQuotation(String token, String customerId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/quotations")
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
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}
}
