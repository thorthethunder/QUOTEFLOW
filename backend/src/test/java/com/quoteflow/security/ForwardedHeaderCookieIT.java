package com.quoteflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behind a TLS-terminating proxy, Secure cookies must still be emitted when the app
 * is configured with secure cookies and sees X-Forwarded-Proto=https.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"quoteflow.security.refresh-cookie.secure=true",
		"server.forward-headers-strategy=framework"
})
class ForwardedHeaderCookieIT extends PostgresIntegrationTest {

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
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM app_users");
		jdbcTemplate.update("DELETE FROM subscriptions");
		jdbcTemplate.update("DELETE FROM businesses");
	}

	@Test
	void registerEmitsSecureRefreshCookieWhenForwardedProtoIsHttps() throws Exception {
		String email = "fwd+" + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/register")
						.header("X-Forwarded-Proto", "https")
						.header("X-Forwarded-Host", "api.example.com")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", "Fwd Co",
								"firstName", "A",
								"lastName", "B",
								"email", email,
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "INR"))))
				.andExpect(status().isCreated())
				.andExpect(header().string("Set-Cookie", containsString("qf_refresh=")))
				.andExpect(header().string("Set-Cookie", containsString("Secure")));
	}
}
