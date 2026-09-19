package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiProviderType;
import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.business.BusinessStatus;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.identity.AppUser;
import com.quoteflow.identity.TenantRole;
import com.quoteflow.identity.UserRepository;
import com.quoteflow.identity.UserStatus;
import com.quoteflow.subscription.PlanId;
import com.quoteflow.subscription.SubscriptionService;
import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AiUsageEntitlementServiceTest extends PostgresIntegrationTest {

	private final AiEntitlementService entitlementService;
	private final AiUsageRecorder usageRecorder;
	private final BusinessRepository businessRepository;
	private final UserRepository userRepository;
	private final SubscriptionService subscriptionService;
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	AiUsageEntitlementServiceTest(
			AiEntitlementService entitlementService,
			AiUsageRecorder usageRecorder,
			BusinessRepository businessRepository,
			UserRepository userRepository,
			SubscriptionService subscriptionService,
			JdbcTemplate jdbcTemplate) {
		this.entitlementService = entitlementService;
		this.usageRecorder = usageRecorder;
		this.businessRepository = businessRepository;
		this.userRepository = userRepository;
		this.subscriptionService = subscriptionService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	void clean() {
		jdbcTemplate.execute("TRUNCATE TABLE businesses RESTART IDENTITY CASCADE");
	}

	@Test
	void goldenUsageAllowsRequest19And20ThenBlocks21() {
		AppUser user = user(PlanId.FREE);
		for (int i = 0; i < 18; i++) {
			entitlementService.consumeAllowance(user.getBusiness().getId(), user.getId(), AiFeature.QUOTE_DRAFT, "quote_draft");
		}

		entitlementService.consumeAllowance(user.getBusiness().getId(), user.getId(), AiFeature.QUOTE_DRAFT, "quote_draft");
		entitlementService.consumeAllowance(user.getBusiness().getId(), user.getId(), AiFeature.QUOTE_DRAFT, "quote_draft");

		assertThatThrownBy(() -> entitlementService.consumeAllowance(
				user.getBusiness().getId(), user.getId(), AiFeature.QUOTE_DRAFT, "quote_draft"))
				.isInstanceOf(DomainApiException.class)
				.extracting("code")
				.isEqualTo("AI_USAGE_LIMIT_REACHED");
		assertThat(used(user.getBusiness().getId(), AiFeature.QUOTE_DRAFT)).isEqualTo(20);
		assertThat(customerAllowanceEvents(user.getBusiness().getId(), AiFeature.QUOTE_DRAFT)).isEqualTo(20);
	}

	@Test
	void concurrentLastAllowanceAllowsExactlyOneProviderAttempt() throws Exception {
		AppUser user = user(PlanId.FREE);
		for (int i = 0; i < 19; i++) {
			entitlementService.consumeAllowance(user.getBusiness().getId(), user.getId(), AiFeature.QUOTE_DRAFT, "quote_draft");
		}
		AtomicInteger providerExecutions = new AtomicInteger();
		Callable<Boolean> task = () -> {
			try {
				entitlementService.consumeAllowance(user.getBusiness().getId(), user.getId(), AiFeature.QUOTE_DRAFT, "quote_draft");
				providerExecutions.incrementAndGet();
				return true;
			} catch (DomainApiException ex) {
				assertThat(ex.getCode()).isEqualTo("AI_USAGE_LIMIT_REACHED");
				return false;
			}
		};

		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(task);
			var second = executor.submit(task);
			int allowed = (Boolean.TRUE.equals(first.get()) ? 1 : 0)
					+ (Boolean.TRUE.equals(second.get()) ? 1 : 0);
			assertThat(allowed).isEqualTo(1);
		}

		assertThat(providerExecutions.get()).isEqualTo(1);
		assertThat(used(user.getBusiness().getId(), AiFeature.QUOTE_DRAFT)).isEqualTo(20);
	}

	@Test
	void disabledFeatureIsRejectedBeforeUsage() {
		AppUser user = user(PlanId.FREE);

		assertThatThrownBy(() -> entitlementService.consumeAllowance(
				user.getBusiness().getId(), user.getId(), AiFeature.PROVIDER_SMOKE, "provider_smoke"))
				.isInstanceOf(DomainApiException.class)
				.extracting("code")
				.isEqualTo("AI_FEATURE_NOT_ENTITLED");
		assertThat(customerAllowanceEvents(user.getBusiness().getId(), AiFeature.PROVIDER_SMOKE)).isZero();
	}

	@Test
	void technicalProviderUsageIsSeparateFromCustomerAllowanceAndStoresNoContent() {
		AppUser user = user(PlanId.PRO);
		entitlementService.consumeAllowance(user.getBusiness().getId(), user.getId(), AiFeature.KNOWLEDGE_QUERY, "knowledge_query");
		usageRecorder.record(AiUsageEvent.embedding(
				AiProviderType.OLLAMA,
				"OLLAMA",
				"mxbai-embed-large",
				AiFeature.KNOWLEDGE_QUERY,
				"knowledge_query_embedding",
				true,
				null,
				12L,
				42,
				1,
				user.getBusiness().getId(),
				user.getId(),
				"query"));
		usageRecorder.record(new AiUsageEvent(
				AiProviderType.OLLAMA,
				"OLLAMA",
				"qwen3:8b",
				AiFeature.BUSINESS_KNOWLEDGE,
				true,
				null,
				25L,
				null,
				null,
				user.getBusiness().getId(),
				user.getId()));

		assertThat(customerAllowanceEvents(user.getBusiness().getId(), AiFeature.KNOWLEDGE_QUERY)).isEqualTo(1);
		assertThat(technicalEvents(user.getBusiness().getId())).isEqualTo(2);
		assertThat(ollamaCostSum(user.getBusiness().getId())).isEqualByComparingTo("0.00000000");
		assertThat(hasContentColumns()).isFalse();
	}

	@Test
	void upgradeAndDowngradeUseCurrentPlanWithoutResettingUsage() {
		AppUser user = user(PlanId.FREE);
		for (int i = 0; i < 5; i++) {
			entitlementService.consumeAllowance(user.getBusiness().getId(), user.getId(), AiFeature.QUOTE_DRAFT, "quote_draft");
		}
		subscriptionService.forcePlanForTests(user.getBusiness().getId(), PlanId.PRO);

		var upgraded = entitlementService.summary(user.getBusiness().getId(), AiFeature.QUOTE_DRAFT);
		assertThat(upgraded.used()).isEqualTo(5);
		assertThat(upgraded.limit()).isEqualTo(100);
		assertThat(upgraded.remaining()).isEqualTo(95);

		subscriptionService.forcePlanForTests(user.getBusiness().getId(), PlanId.FREE);
		var downgraded = entitlementService.summary(user.getBusiness().getId(), AiFeature.QUOTE_DRAFT);
		assertThat(downgraded.used()).isEqualTo(5);
		assertThat(downgraded.limit()).isEqualTo(20);
		assertThat(downgraded.remaining()).isEqualTo(15);
	}

	private AppUser user(PlanId plan) {
		Business business = businessRepository.save(new Business("AI Usage Co " + UUID.randomUUID(), "INR", "Asia/Kolkata", BusinessStatus.ACTIVE));
		AppUser user = userRepository.save(new AppUser(
				business,
				"ai-usage-" + UUID.randomUUID() + "@example.com",
				"$2a$12$abcdefghijklmnopqrstuv",
				TenantRole.OWNER,
				UserStatus.ACTIVE));
		subscriptionService.ensureDefaultFreeSubscription(business.getId());
		subscriptionService.forcePlanForTests(business.getId(), plan);
		return user;
	}

	private int used(UUID businessId, AiFeature feature) {
		Integer used = jdbcTemplate.query("""
				SELECT used_count FROM ai_usage_allowance_buckets
				 WHERE business_id = ? AND feature = ?
				""", rs -> rs.next() ? rs.getInt(1) : 0, businessId, feature.name());
		return used == null ? 0 : used;
	}

	private long customerAllowanceEvents(UUID businessId, AiFeature feature) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM ai_usage_events
				 WHERE business_id = ? AND feature = ? AND usage_type = 'CUSTOMER_ALLOWANCE'
				""", Long.class, businessId, feature.name());
		return count == null ? 0L : count;
	}

	private long technicalEvents(UUID businessId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM ai_usage_events
				 WHERE business_id = ? AND usage_type <> 'CUSTOMER_ALLOWANCE'
				""", Long.class, businessId);
		return count == null ? 0L : count;
	}

	private BigDecimal ollamaCostSum(UUID businessId) {
		BigDecimal sum = jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(estimated_provider_cost), 0) FROM ai_usage_events
				 WHERE business_id = ? AND provider = 'OLLAMA'
				""", BigDecimal.class, businessId);
		return sum == null ? BigDecimal.ZERO : sum;
	}

	private boolean hasContentColumns() {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM information_schema.columns
				 WHERE table_name = 'ai_usage_events'
				   AND column_name IN ('prompt', 'response', 'content', 'document_chunk', 'email_body')
				""", Long.class);
		return count != null && count > 0;
	}
}
