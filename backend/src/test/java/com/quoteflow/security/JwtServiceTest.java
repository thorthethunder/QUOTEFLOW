package com.quoteflow.security;

import com.quoteflow.identity.TenantRole;
import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest extends PostgresIntegrationTest {

	@Autowired
	private JwtService jwtService;

	@Test
	void issuesAndParsesAccessTokenWithRequiredClaims() {
		AuthenticatedUser user = new AuthenticatedUser(
				UUID.randomUUID(),
				UUID.randomUUID(),
				TenantRole.ADMIN,
				"admin@example.com");

		JwtService.IssuedAccessToken issued = jwtService.issueAccessToken(user);
		assertThat(issued.expiresInSeconds()).isEqualTo(900);

		AuthenticatedUser parsed = jwtService.parseAndValidate(issued.token());
		assertThat(parsed.getUserId()).isEqualTo(user.getUserId());
		assertThat(parsed.getBusinessId()).isEqualTo(user.getBusinessId());
		assertThat(parsed.getTenantRole()).isEqualTo(TenantRole.ADMIN);
	}

	@Test
	void rejectsMalformedToken() {
		assertThatThrownBy(() -> jwtService.parseAndValidate("not.a.jwt"))
				.isInstanceOf(InvalidAccessTokenException.class);
	}
}
