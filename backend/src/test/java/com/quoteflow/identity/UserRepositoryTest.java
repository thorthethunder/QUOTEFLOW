package com.quoteflow.identity;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.business.BusinessStatus;
import com.quoteflow.common.EmailNormalizer;
import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest extends PostgresIntegrationTest {

	@Autowired
	private BusinessRepository businessRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createsUserUnderBusinessAndNormalizesEmail() {
		Business business = businessRepository.saveAndFlush(
				new Business("Tenant A", "INR", "Asia/Kolkata", BusinessStatus.ACTIVE));

		AppUser user = new AppUser(
				business,
				"  Owner@Example.COM ",
				"$2a$10$placeholderhashvalueforlengthcheckxxxxxxxxxxxx",
				TenantRole.OWNER,
				UserStatus.ACTIVE);
		user.setFirstName("Ada");

		AppUser saved = userRepository.saveAndFlush(user);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getEmail()).isEqualTo("owner@example.com");
		assertThat(saved.getBusiness().getId()).isEqualTo(business.getId());
		assertThat(saved.getTenantRole()).isEqualTo(TenantRole.OWNER);
		assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(saved.isEmailVerified()).isFalse();
		assertThat(saved.getLastLoginAt()).isNull();
		assertThat(saved.getPasswordHash()).startsWith("$2a$10$");
		assertThat(saved.getPasswordHash().length()).isLessThanOrEqualTo(255);
		assertThat(EmailNormalizer.normalize("  Owner@Example.COM ")).isEqualTo("owner@example.com");
	}

	@Test
	void enforcesGlobalEmailUniqueness() {
		Business a = businessRepository.saveAndFlush(
				new Business("Business A", "INR", "Asia/Kolkata", BusinessStatus.ACTIVE));
		Business b = businessRepository.saveAndFlush(
				new Business("Business B", "USD", "America/New_York", BusinessStatus.ACTIVE));

		userRepository.saveAndFlush(new AppUser(
				a, "shared@example.com", "hash-a-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", TenantRole.OWNER, UserStatus.ACTIVE));

		assertThatThrownBy(() -> userRepository.saveAndFlush(new AppUser(
				b, "Shared@Example.com", "hash-b-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", TenantRole.STAFF, UserStatus.ACTIVE)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void schemaHasPasswordHashNotPasswordColumn() {
		Integer passwordColumns = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_name = 'app_users' AND column_name = 'password'
						""",
				Integer.class);
		Integer hashColumns = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_name = 'app_users' AND column_name = 'password_hash'
						""",
				Integer.class);

		assertThat(passwordColumns).isZero();
		assertThat(hashColumns).isOne();
	}

	@Test
	void keepsUsersInSeparateBusinessesDistinct() {
		Business businessA = businessRepository.saveAndFlush(
				new Business("Business A", "INR", "Asia/Kolkata", BusinessStatus.ACTIVE));
		Business businessB = businessRepository.saveAndFlush(
				new Business("Business B", "USD", "America/New_York", BusinessStatus.ACTIVE));

		AppUser userA = userRepository.saveAndFlush(new AppUser(
				businessA, "a@tenant.test", "hash-a-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", TenantRole.OWNER, UserStatus.ACTIVE));
		AppUser userB = userRepository.saveAndFlush(new AppUser(
				businessB, "b@tenant.test", "hash-b-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", TenantRole.ADMIN, UserStatus.ACTIVE));

		List<AppUser> usersOfA = userRepository.findByBusiness_Id(businessA.getId());
		List<AppUser> usersOfB = userRepository.findByBusiness_Id(businessB.getId());

		assertThat(usersOfA).extracting(AppUser::getId).containsExactly(userA.getId());
		assertThat(usersOfB).extracting(AppUser::getId).containsExactly(userB.getId());
		assertThat(userA.getBusiness().getId()).isNotEqualTo(userB.getBusiness().getId());
	}
}
