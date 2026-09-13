package com.quoteflow.business;

import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BusinessRepositoryTest extends PostgresIntegrationTest {

	@Autowired
	private BusinessRepository businessRepository;

	@Test
	void createsBusinessWithRequiredFieldsAndAuditTimestamps() {
		Business business = new Business("Acme Services", "INR", "Asia/Kolkata", BusinessStatus.ACTIVE);

		Business saved = businessRepository.saveAndFlush(business);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getName()).isEqualTo("Acme Services");
		assertThat(saved.getCurrency()).isEqualTo("INR");
		assertThat(saved.getTimezone()).isEqualTo("Asia/Kolkata");
		assertThat(saved.getStatus()).isEqualTo(BusinessStatus.ACTIVE);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getEmail()).isNull();
		assertThat(saved.getPhone()).isNull();
	}

	@Test
	void rejectsInvalidCurrencyViaDatabaseConstraint() {
		Business business = new Business("Bad Currency Co", "IN", "Asia/Kolkata", BusinessStatus.ACTIVE);

		assertThatThrownBy(() -> businessRepository.saveAndFlush(business))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
