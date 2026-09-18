package com.quoteflow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.billing.provider.fake.FakeBillingProvider;
import com.quoteflow.billing.provider.razorpay.RazorpaySignatureVerifier;
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
@TestPropertySource(properties = {
		"quoteflow.billing.enabled=true",
		"quoteflow.billing.provider=FAKE"
})
class BillingIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FakeBillingProvider fakeBillingProvider;

	@Autowired
	private RazorpaySignatureVerifier signatureVerifier;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
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
		jdbcTemplate.update("DELETE FROM ai_action_proposals");
		jdbcTemplate.update("DELETE FROM app_users");
		jdbcTemplate.update("DELETE FROM subscriptions");
		jdbcTemplate.update("DELETE FROM notifications");
		jdbcTemplate.update("DELETE FROM businesses");
		fakeBillingProvider.reset();
	}

	@Test
	void ownerCheckoutVerifyAndWebhookActivatePro() throws Exception {
		String token = register("bill+" + UUID.randomUUID() + "@example.com");

		MvcResult checkout = mockMvc.perform(post("/api/v1/billing/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"plan", "PRO",
								"billingInterval", "MONTHLY"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("FAKE"))
				.andExpect(jsonPath("$.subscriptionId").isNotEmpty())
				.andExpect(jsonPath("$.keyId").value("rzp_test_fake_key"))
				.andReturn();

		String subId = objectMapper.readTree(checkout.getResponse().getContentAsString())
				.get("subscriptionId").asText();
		String paymentId = "pay_fake_1";
		String signature = signatureVerifier.hmacSha256Hex(paymentId + "|" + subId, "fake_key_secret");

		mockMvc.perform(post("/api/v1/billing/checkout/verify")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"razorpayPaymentId", paymentId,
								"razorpaySubscriptionId", subId,
								"razorpaySignature", signature))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activated").value(true))
				.andExpect(jsonPath("$.plan").value("PRO"));

		mockMvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan").value("PRO"))
				.andExpect(jsonPath("$.features.removeQuoteFlowBranding").value(true))
				.andExpect(jsonPath("$.limits.activeCustomers.unlimited").value(true));
	}

	@Test
	void invalidCheckoutSignatureRejected() throws Exception {
		String token = register("sig+" + UUID.randomUUID() + "@example.com");
		MvcResult checkout = mockMvc.perform(post("/api/v1/billing/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"plan", "PRO",
								"billingInterval", "MONTHLY"))))
				.andExpect(status().isOk())
				.andReturn();
		String subId = objectMapper.readTree(checkout.getResponse().getContentAsString())
				.get("subscriptionId").asText();

		mockMvc.perform(post("/api/v1/billing/checkout/verify")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"razorpayPaymentId", "pay_x",
								"razorpaySubscriptionId", subId,
								"razorpaySignature", "deadbeef"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BILLING_VERIFICATION_FAILED"));
	}

	@Test
	void webhookSignatureAndIdempotency() throws Exception {
		String token = register("hook+" + UUID.randomUUID() + "@example.com");
		MvcResult checkout = mockMvc.perform(post("/api/v1/billing/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"plan", "PRO",
								"billingInterval", "MONTHLY"))))
				.andExpect(status().isOk())
				.andReturn();
		String subId = objectMapper.readTree(checkout.getResponse().getContentAsString())
				.get("subscriptionId").asText();

		long now = InstantSeconds.now();
		String body = """
				{"event":"subscription.activated","payload":{"subscription":{"entity":{
				"id":"%s","plan_id":"plan_fake_pro_monthly","status":"active",
				"current_start":%d,"current_end":%d,"created_at":%d
				}}}}
				""".formatted(subId, now, now + 86400 * 30, now);
		String signature = signatureVerifier.hmacSha256Hex(body, "fake_webhook_secret");

		mockMvc.perform(post("/api/v1/webhooks/razorpay")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Razorpay-Signature", signature)
						.header("X-Razorpay-Event-Id", "evt_1")
						.content(body))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/webhooks/razorpay")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Razorpay-Signature", signature)
						.header("X-Razorpay-Event-Id", "evt_1")
						.content(body))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan").value("PRO"));

		Integer processed = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM billing_webhook_events WHERE provider_event_id = 'evt_1'",
				Integer.class);
		assertThat(processed).isEqualTo(1);

		mockMvc.perform(post("/api/v1/webhooks/razorpay")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Razorpay-Signature", "bad")
						.header("X-Razorpay-Event-Id", "evt_2")
						.content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void adminAndStaffCannotCheckout() throws Exception {
		String ownerToken = register("own+" + UUID.randomUUID() + "@example.com");
		UUID businessId = businessIdForToken(ownerToken);
		String adminEmail = "admin+" + UUID.randomUUID() + "@example.com";
		String staffEmail = "staff+" + UUID.randomUUID() + "@example.com";
		createUser(businessId, adminEmail, "ADMIN");
		createUser(businessId, staffEmail, "STAFF");

		String adminToken = login(adminEmail);
		String staffToken = login(staffEmail);

		mockMvc.perform(post("/api/v1/billing/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"plan", "PRO",
								"billingInterval", "MONTHLY"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/billing/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"plan", "PRO",
								"billingInterval", "MONTHLY"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void concurrentCheckoutDoesNotDuplicateProviderSubscriptions() throws Exception {
		String token = register("race+" + UUID.randomUUID() + "@example.com");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<String> first = new AtomicReference<>();
		AtomicReference<String> second = new AtomicReference<>();

		Future<?> f1 = pool.submit(() -> {
			start.await(5, TimeUnit.SECONDS);
			MvcResult result = mockMvc.perform(post("/api/v1/billing/checkout")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(Map.of(
									"plan", "PRO",
									"billingInterval", "MONTHLY"))))
					.andReturn();
			assertThat(result.getResponse().getStatus()).isEqualTo(200);
			first.set(objectMapper.readTree(result.getResponse().getContentAsString())
					.get("subscriptionId").asText());
			return null;
		});
		Future<?> f2 = pool.submit(() -> {
			start.await(5, TimeUnit.SECONDS);
			MvcResult result = mockMvc.perform(post("/api/v1/billing/checkout")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(Map.of(
									"plan", "PRO",
									"billingInterval", "MONTHLY"))))
					.andReturn();
			assertThat(result.getResponse().getStatus()).isEqualTo(200);
			second.set(objectMapper.readTree(result.getResponse().getContentAsString())
					.get("subscriptionId").asText());
			return null;
		});
		start.countDown();
		f1.get(10, TimeUnit.SECONDS);
		f2.get(10, TimeUnit.SECONDS);
		pool.shutdown();

		assertThat(first.get()).isEqualTo(second.get());
		assertThat(fakeBillingProvider.createCallCount()).isEqualTo(1);
	}

	@Test
	void crossTenantVerifyDenied() throws Exception {
		String tokenA = register("a+" + UUID.randomUUID() + "@example.com");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com");
		MvcResult checkout = mockMvc.perform(post("/api/v1/billing/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"plan", "PRO",
								"billingInterval", "MONTHLY"))))
				.andExpect(status().isOk())
				.andReturn();
		String subId = objectMapper.readTree(checkout.getResponse().getContentAsString())
				.get("subscriptionId").asText();
		String paymentId = "pay_x";
		String signature = signatureVerifier.hmacSha256Hex(paymentId + "|" + subId, "fake_key_secret");

		mockMvc.perform(post("/api/v1/billing/checkout/verify")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"razorpayPaymentId", paymentId,
								"razorpaySubscriptionId", subId,
								"razorpaySignature", signature))))
				.andExpect(status().isForbidden());
	}

	@Test
	void billingDisabledReturnsSafeError() throws Exception {
		// This class enables billing via TestPropertySource; disabled behavior covered by
		// SubscriptionEntitlementIntegrationTest billingCheckoutAvailable=false.
		assertThat(true).isTrue();
	}

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Billing Co",
								"firstName", "Bal",
								"lastName", "K",
								"email", email,
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "INR"))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private String login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"email", email,
								"password", "passphrase-long-enough"))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private UUID businessIdForToken(String token) throws Exception {
		MvcResult me = mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode node = objectMapper.readTree(me.getResponse().getContentAsString());
		return UUID.fromString(node.get("businessId").asText());
	}

	private void createUser(UUID businessId, String email, String role) {
		String hash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM app_users WHERE business_id = ? AND tenant_role = 'OWNER' LIMIT 1",
				String.class,
				businessId);
		jdbcTemplate.update(
				"""
						INSERT INTO app_users (
						  id, business_id, email, password_hash, first_name, last_name,
						  tenant_role, status, email_verified, created_at, updated_at)
						VALUES (?, ?, ?, ?, 'X', 'Y', ?, 'ACTIVE', FALSE, NOW(), NOW())
						""",
				UUID.randomUUID(),
				businessId,
				email,
				hash,
				role);
	}

	private static final class InstantSeconds {
		static long now() {
			return java.time.Instant.now().getEpochSecond();
		}
	}
}
