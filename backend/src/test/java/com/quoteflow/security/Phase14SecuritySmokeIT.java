package com.quoteflow.security;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class Phase14SecuritySmokeIT extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CorrelationIdFilter correlationIdFilter;

	@Autowired
	private SensitiveApiCacheControlFilter sensitiveApiCacheControlFilter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(correlationIdFilter, sensitiveApiCacheControlFilter)
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
		jdbcTemplate.update("DELETE FROM ai_action_proposals");
		jdbcTemplate.update("DELETE FROM app_users");
		jdbcTemplate.update("DELETE FROM subscriptions");
		jdbcTemplate.update("DELETE FROM businesses");
	}

	@Test
	void securityHeadersCorrelationCacheAndAnonymousRejection() throws Exception {
		MvcResult health = mockMvc.perform(get("/actuator/health")
						.header(CorrelationIdFilter.HEADER, "phase14-smoke-corr-01"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Referrer-Policy", "no-referrer"))
				.andReturn();
		assertThat(health.getResponse().getHeader("Permissions-Policy")).contains("camera=()");
		// Correlation filter is a servlet Filter bean; assert on an authenticated API call which MockMvc always routes.
		String email = "smoke+" + UUID.randomUUID() + "@example.com";
		MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
						.header(CorrelationIdFilter.HEADER, "phase14-smoke-corr-01")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Smoke Co",
								"firstName", "A",
								"lastName", "B",
								"email", email,
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "INR"))))
				.andExpect(status().isCreated())
				.andExpect(header().string(CorrelationIdFilter.HEADER, "phase14-smoke-corr-01"))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
				.andReturn();
		JsonNode body = objectMapper.readTree(registered.getResponse().getContentAsString());
		String access = body.get("accessToken").asText();
		String refresh = body.get("refreshToken").asText();

		mockMvc.perform(get("/api/v1/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
						.header(CorrelationIdFilter.HEADER, "phase14-smoke-corr-02"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(header().string(CorrelationIdFilter.HEADER, "phase14-smoke-corr-02"));

		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf().useInvalidToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void oversizedPageSizeRejected() throws Exception {
		String email = "page+" + UUID.randomUUID() + "@example.com";
		MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Page Co",
								"firstName", "A",
								"lastName", "B",
								"email", email,
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "INR"))))
				.andExpect(status().isCreated())
				.andReturn();
		String access = objectMapper.readTree(registered.getResponse().getContentAsString()).get("accessToken").asText();

		mockMvc.perform(get("/api/v1/customers")
						.param("size", "1000000")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
				.andExpect(status().isBadRequest());
	}
}
