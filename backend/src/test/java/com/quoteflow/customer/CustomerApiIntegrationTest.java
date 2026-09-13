package com.quoteflow.customer;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class CustomerApiIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	void anonymousRequestsAreUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/customers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"X\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/v1/customers/" + UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"X\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/customers/" + UUID.randomUUID() + "/archive"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createListGetUpdateArchiveAndSearch() throws Exception {
		String token = registerAndToken("crud+" + UUID.randomUUID() + "@example.com", "Crud Co");

		MvcResult created = mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"displayName", "  Ada Customer  ",
								"email", "  ADA@Example.COM ",
								"phone", "+91 98765 43210",
								"companyName", "Lovelace Ltd",
								"countryCode", "in",
								"notes", "Preferred contact mornings"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.displayName").value("Ada Customer"))
				.andExpect(jsonPath("$.email").value("ada@example.com"))
				.andExpect(jsonPath("$.countryCode").value("IN"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andReturn();

		String customerId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].displayName").value("Ada Customer"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.param("q", "lovelace"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/api/v1/customers/" + customerId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.companyName").value("Lovelace Ltd"));

		mockMvc.perform(put("/api/v1/customers/" + customerId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"displayName", "Ada Updated",
								"email", "ada.updated@example.com",
								"phone", "+91 11111 22222",
								"companyName", "Lovelace Ltd",
								"city", "Bengaluru"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Ada Updated"))
				.andExpect(jsonPath("$.city").value("Bengaluru"));

		mockMvc.perform(post("/api/v1/customers/" + customerId + "/archive")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ARCHIVED"));

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.param("status", "ARCHIVED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void validationRejectsBlankNameInvalidEmailOversizedPageAndBadSort() throws Exception {
		String token = registerAndToken("val+" + UUID.randomUUID() + "@example.com", "Val Co");

		mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"displayName", "Valid Name",
								"email", "not-an-email"))))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.param("size", "101"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.param("sort", "passwordHash,asc"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void tenantIsolationPreventsCrossTenantAccessAndEnumeration() throws Exception {
		String tokenA = registerAndToken("a+" + UUID.randomUUID() + "@example.com", "Business A");
		String tokenB = registerAndToken("b+" + UUID.randomUUID() + "@example.com", "Business B");

		MvcResult createdA = mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"displayName", "Secret Customer A",
								"email", "secret-a@example.com",
								"companyName", "Alpha Corp"))))
				.andExpect(status().isCreated())
				.andReturn();
		String customerAId = objectMapper.readTree(createdA.getResponse().getContentAsString()).get("id").asText();

		MvcResult createdB = mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"displayName", "Secret Customer B",
								"email", "secret-b@example.com"))))
				.andExpect(status().isCreated())
				.andReturn();
		String customerBId = objectMapper.readTree(createdB.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].displayName").value("Secret Customer A"));

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.param("q", "Secret Customer B"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.param("q", "secret-b@example.com"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/v1/customers/" + customerBId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(put("/api/v1/customers/" + customerBId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", "Hacked"))))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/customers/" + customerBId + "/archive")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/customers/" + customerAId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(status().isNotFound());

		JsonNode stillA = objectMapper.readTree(mockMvc.perform(get("/api/v1/customers/" + customerAId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());
		assertThat(stillA.get("displayName").asText()).isEqualTo("Secret Customer A");
	}

	@Test
	void createIgnoresClientSuppliedBusinessOwnershipFields() throws Exception {
		String token = registerAndToken("mass+" + UUID.randomUUID() + "@example.com", "Mass Co");
		String foreignBusinessId = UUID.randomUUID().toString();

		MvcResult result = mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "Owned By Auth Tenant",
								  "businessId": "%s",
								  "id": "%s",
								  "status": "ARCHIVED"
								}
								""".formatted(foreignBusinessId, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.displayName").value("Owned By Auth Tenant"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.has("businessId")).isFalse();
	}

	private String registerAndToken(String email, String businessName) throws Exception {
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
}
