package com.quoteflow.identity;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.business.BusinessStatus;
import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenRepositoryTest extends PostgresIntegrationTest {

	@Autowired
	private BusinessRepository businessRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createsRefreshTokenWithHashAndExpiration() {
		AppUser user = createUser("token-owner@example.com");
		Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

		RefreshToken token = new RefreshToken(user, "sha256-hash-value-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", expiresAt);
		RefreshToken saved = refreshTokenRepository.saveAndFlush(token);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getUser().getId()).isEqualTo(user.getId());
		assertThat(saved.getTokenHash()).startsWith("sha256-");
		assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(saved.getRevokedAt()).isNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(refreshTokenRepository.findByTokenHash(saved.getTokenHash())).isPresent();
	}

	@Test
	void enforcesTokenHashUniqueness() {
		AppUser user = createUser("hash-unique@example.com");
		String hash = "duplicate-token-hash-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
		Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);

		refreshTokenRepository.saveAndFlush(new RefreshToken(user, hash, expiresAt));

		assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(new RefreshToken(user, hash, expiresAt)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void schemaHasTokenHashNotRawTokenColumn() {
		Integer tokenColumns = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_name = 'refresh_tokens' AND column_name = 'token'
						""",
				Integer.class);
		Integer hashColumns = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_name = 'refresh_tokens' AND column_name = 'token_hash'
						""",
				Integer.class);

		assertThat(tokenColumns).isZero();
		assertThat(hashColumns).isOne();
	}

	private AppUser createUser(String email) {
		Business business = businessRepository.saveAndFlush(
				new Business("Token Biz", "INR", "Asia/Kolkata", BusinessStatus.ACTIVE));
		return userRepository.saveAndFlush(new AppUser(
				business,
				email,
				"hash-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
				TenantRole.OWNER,
				UserStatus.ACTIVE));
	}
}
