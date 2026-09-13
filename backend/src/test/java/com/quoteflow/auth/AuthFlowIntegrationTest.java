package com.quoteflow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.business.BusinessStatus;
import com.quoteflow.identity.AppUser;
import com.quoteflow.identity.RefreshTokenRepository;
import com.quoteflow.identity.TenantRole;
import com.quoteflow.identity.UserRepository;
import com.quoteflow.identity.UserStatus;
import com.quoteflow.security.JwtService;
import com.quoteflow.security.SecurityProperties;
import com.quoteflow.support.PostgresIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BusinessRepository businessRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RefreshTokenHasher refreshTokenHasher;

	@Autowired
	private SecurityProperties securityProperties;

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
	void registerCreatesBusinessOwnerAndReturnsTokens() throws Exception {
		String email = "owner+" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Acme Co",
								"firstName", "Ada",
								"lastName", "Lovelace",
								"email", "  " + email.toUpperCase() + "  ",
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "INR"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.user.tenantRole").value("OWNER"))
				.andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		AppUser user = userRepository.findByEmailWithBusiness(email.toLowerCase()).orElseThrow();
		assertThat(passwordEncoder.matches("passphrase-long-enough", user.getPasswordHash())).isTrue();
		assertThat(user.getPasswordHash()).doesNotContain("passphrase-long-enough");
		assertThat(user.getTenantRole()).isEqualTo(TenantRole.OWNER);
		assertThat(user.getBusiness().getStatus()).isEqualTo(BusinessStatus.ACTIVE);

		String refreshHash = refreshTokenHasher.hash(body.get("refreshToken").asText());
		assertThat(refreshTokenRepository.findByTokenHash(refreshHash)).isPresent();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.columns WHERE table_name='refresh_tokens' AND column_name='token'",
				Integer.class)).isZero();
	}

	@Test
	void registerRejectsDuplicateEmailShortPasswordInvalidCurrencyTimezone() throws Exception {
		String email = "dup+" + UUID.randomUUID() + "@example.com";
		register(email, "passphrase-long-enough");

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registrationJson(email, "another-long-password")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registrationJson("short+" + UUID.randomUUID() + "@example.com", "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Bad Currency",
								"firstName", "A",
								"lastName", "B",
								"email", "cur+" + UUID.randomUUID() + "@example.com",
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "₹₹₹"))))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Bad Tz",
								"firstName", "A",
								"lastName", "B",
								"email", "tz+" + UUID.randomUUID() + "@example.com",
								"password", "passphrase-long-enough",
								"timezone", "IST",
								"currency", "INR"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void loginAndMeAndRefreshAndLogoutWork() throws Exception {
		String email = "login+" + UUID.randomUUID() + "@example.com";
		register(email, "passphrase-long-enough");

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"email", email.toUpperCase(),
								"password", "passphrase-long-enough"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
		String access = loginBody.get("accessToken").asText();
		String refresh = loginBody.get("refreshToken").asText();
		UUID businessId = UUID.fromString(loginBody.get("user").get("businessId").asText());

		AppUser user = userRepository.findByEmail(email).orElseThrow();
		assertThat(user.getLastLoginAt()).isNotNull();

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.businessId").value(businessId.toString()))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn();

		String rotated = objectMapper.readTree(refreshed.getResponse().getContentAsString()).get("refreshToken").asText();
		assertThat(rotated).isNotEqualTo(refresh);

		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/logout")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", rotated))))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", rotated))))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/logout")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", rotated))))
				.andExpect(status().isNoContent());
	}

	@Test
	void loginFailuresAreGenericAndStatusChecksWork() throws Exception {
		String email = "fail+" + UUID.randomUUID() + "@example.com";
		register(email, "passphrase-long-enough");

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"email", email,
								"password", "wrong-password-xx"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password."));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"email", "missing+" + UUID.randomUUID() + "@example.com",
								"password", "passphrase-long-enough"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password."));

		AppUser user = userRepository.findByEmail(email).orElseThrow();
		user.setStatus(UserStatus.DISABLED);
		userRepository.saveAndFlush(user);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"email", email,
								"password", "passphrase-long-enough"))))
				.andExpect(status().isForbidden());

		String suspendedEmail = "susp+" + UUID.randomUUID() + "@example.com";
		register(suspendedEmail, "passphrase-long-enough");
		AppUser suspendedUser = userRepository.findByEmailWithBusiness(suspendedEmail).orElseThrow();
		Business business = suspendedUser.getBusiness();
		business.setStatus(BusinessStatus.SUSPENDED);
		businessRepository.saveAndFlush(business);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"email", suspendedEmail,
								"password", "passphrase-long-enough"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void meRequiresValidJwtAndTenantContextIsFromToken() throws Exception {
		mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());

		String emailA = "a+" + UUID.randomUUID() + "@example.com";
		String emailB = "b+" + UUID.randomUUID() + "@example.com";
		JsonNode a = objectMapper.readTree(register(emailA, "passphrase-long-enough").getResponse().getContentAsString());
		JsonNode b = objectMapper.readTree(register(emailB, "passphrase-long-enough").getResponse().getContentAsString());

		String accessA = a.get("accessToken").asText();
		String businessA = a.get("user").get("businessId").asText();
		String businessB = b.get("user").get("businessId").asText();
		assertThat(businessA).isNotEqualTo(businessB);

		mockMvc.perform(get("/api/v1/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
						.param("businessId", businessB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"businessId\":\"" + businessB + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.businessId").value(businessA));

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());

		String tampered = accessA.substring(0, accessA.length() - 4) + "xxxx";
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
				.andExpect(status().isUnauthorized());

		String wrongIssuer = Jwts.builder()
				.issuer("other-issuer")
				.audience().add(securityProperties.getJwt().getAudience()).and()
				.subject(a.get("user").get("userId").asText())
				.claim(JwtService.CLAIM_BUSINESS_ID, businessA)
				.claim(JwtService.CLAIM_TENANT_ROLE, "OWNER")
				.expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
				.signWith(Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)))
				.compact();
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongIssuer))
				.andExpect(status().isUnauthorized());

		String expired = Jwts.builder()
				.issuer(securityProperties.getJwt().getIssuer())
				.audience().add(securityProperties.getJwt().getAudience()).and()
				.subject(a.get("user").get("userId").asText())
				.claim(JwtService.CLAIM_BUSINESS_ID, businessA)
				.claim(JwtService.CLAIM_TENANT_ROLE, "OWNER")
				.expiration(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
				.signWith(Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)))
				.compact();
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void concurrentRefreshAllowsOnlyOneSuccess() throws Exception {
		String email = "race+" + UUID.randomUUID() + "@example.com";
		JsonNode registered = objectMapper.readTree(register(email, "passphrase-long-enough").getResponse().getContentAsString());
		String refresh = registered.get("refreshToken").asText();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger failures = new AtomicInteger();

		Future<?> f1 = pool.submit(() -> attemptRefresh(refresh, start, successes, failures));
		Future<?> f2 = pool.submit(() -> attemptRefresh(refresh, start, successes, failures));
		start.countDown();
		f1.get();
		f2.get();
		pool.shutdownNow();

		assertThat(successes.get()).isEqualTo(1);
		assertThat(failures.get()).isEqualTo(1);
	}

	@Test
	void corsAllowsConfiguredDevOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "http://localhost:4200")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));

		mockMvc.perform(options("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "http://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void emailCanonicalCheckConstraintExists() {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM pg_constraint
						WHERE conname = 'chk_app_users_email_canonical'
						""",
				Integer.class);
		assertThat(count).isOne();
	}

	private void attemptRefresh(String refresh, CountDownLatch start, AtomicInteger successes, AtomicInteger failures) {
		try {
			start.await();
			int status = mockMvc.perform(post("/api/v1/auth/refresh")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
					.andReturn()
					.getResponse()
					.getStatus();
			if (status == 200) {
				successes.incrementAndGet();
			}
			else {
				failures.incrementAndGet();
			}
		}
		catch (Exception ex) {
			failures.incrementAndGet();
		}
	}

	private MvcResult register(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registrationJson(email, password)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private String registrationJson(String email, String password) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"businessName", "Biz " + email,
				"firstName", "First",
				"lastName", "Last",
				"email", email,
				"password", password,
				"timezone", "Asia/Kolkata",
				"currency", "INR"));
	}
}
