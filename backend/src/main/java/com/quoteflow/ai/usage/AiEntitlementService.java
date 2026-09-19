package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiProviderType;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.subscription.PlanId;
import com.quoteflow.subscription.Subscription;
import com.quoteflow.subscription.SubscriptionRepository;
import com.quoteflow.subscription.UsagePeriod;
import com.quoteflow.subscription.UsageService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AiEntitlementService {

	private final SubscriptionRepository subscriptionRepository;
	private final UsageService usageService;
	private final AiUsagePolicy usagePolicy;
	private final AiUsageLedgerService ledgerService;
	private final JdbcTemplate jdbcTemplate;
	private final Clock clock;

	public AiEntitlementService(
			SubscriptionRepository subscriptionRepository,
			UsageService usageService,
			AiUsagePolicy usagePolicy,
			AiUsageLedgerService ledgerService,
			JdbcTemplate jdbcTemplate,
			Clock clock) {
		this.subscriptionRepository = subscriptionRepository;
		this.usageService = usageService;
		this.usagePolicy = usagePolicy;
		this.ledgerService = ledgerService;
		this.jdbcTemplate = jdbcTemplate;
		this.clock = clock;
	}

	@Transactional
	public AiUsageAllowance consumeAllowance(UUID businessId, UUID userId, AiFeature feature, String operation) {
		Subscription subscription = subscriptionRepository.findByBusinessIdForUpdate(businessId)
				.orElseThrow(() -> new DomainApiException(HttpStatus.FORBIDDEN,
						"SUBSCRIPTION_NOT_FOUND", "Subscription not found for business"));
		PlanId plan = subscription.getPlan();
		AiUsageLimit limit = usagePolicy.limit(plan, feature);
		UsagePeriod period = usageService.currentCalendarMonth(businessId);
		String periodKey = AiUsageLedgerService.periodKey(period.startInclusive());
		if (!limit.enabled()) {
			throw featureNotEntitled(feature, plan, periodKey);
		}
		int used = bucketUsedForUpdate(businessId, feature, period, periodKey);
		if (!limit.unlimited() && used >= limit.monthlyAllowance()) {
			throw limitReached(feature, plan, periodKey, used, limit.monthlyAllowance(), period.endExclusive());
		}
		int next = used + 1;
		updateBucket(businessId, feature, periodKey, next);
		ledgerService.record(AiUsageEvent.customerAllowance(
				feature, operation, businessId, userId, true, null, periodKey));
		return new AiUsageAllowance(feature, plan, periodKey, next, limit.monthlyAllowance(), period.endExclusive());
	}

	@Transactional(readOnly = true)
	public AiUsageSummary summary(UUID businessId, AiFeature feature) {
		PlanId plan = subscriptionRepository.findByBusinessId(businessId).map(Subscription::getPlan).orElse(PlanId.FREE);
		AiUsageLimit limit = usagePolicy.limit(plan, feature);
		UsagePeriod period = usageService.currentCalendarMonth(businessId);
		String periodKey = AiUsageLedgerService.periodKey(period.startInclusive());
		Integer used = jdbcTemplate.query("""
				SELECT used_count FROM ai_usage_allowance_buckets
				 WHERE business_id = ? AND feature = ? AND billing_period_key = ?
				""", rs -> rs.next() ? rs.getInt(1) : 0, businessId, feature.name(), periodKey);
		return new AiUsageSummary(feature, limit.enabled(), used == null ? 0 : used,
				limit.monthlyAllowance(), periodKey, period.endExclusive());
	}

	private int bucketUsedForUpdate(UUID businessId, AiFeature feature, UsagePeriod period, String periodKey) {
		Instant now = Instant.now(clock);
		jdbcTemplate.update("""
				INSERT INTO ai_usage_allowance_buckets (
				  id, business_id, feature, billing_period_key, period_start, period_end,
				  used_count, created_at, updated_at, version
				) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, 0)
				ON CONFLICT (business_id, feature, billing_period_key) DO NOTHING
				""", UUID.randomUUID(), businessId, feature.name(), periodKey,
				Timestamp.from(period.startInclusive()), Timestamp.from(period.endExclusive()),
				Timestamp.from(now), Timestamp.from(now));
		Integer used = jdbcTemplate.query("""
				SELECT used_count FROM ai_usage_allowance_buckets
				 WHERE business_id = ? AND feature = ? AND billing_period_key = ?
				 FOR UPDATE
				""", rs -> rs.next() ? rs.getInt(1) : 0, businessId, feature.name(), periodKey);
		return used == null ? 0 : used;
	}

	private void updateBucket(UUID businessId, AiFeature feature, String periodKey, int used) {
		jdbcTemplate.update("""
				UPDATE ai_usage_allowance_buckets
				   SET used_count = ?, updated_at = ?, version = version + 1
				 WHERE business_id = ? AND feature = ? AND billing_period_key = ?
				""", used, Timestamp.from(Instant.now(clock)), businessId, feature.name(), periodKey);
	}

	private static DomainApiException featureNotEntitled(AiFeature feature, PlanId plan, String periodKey) {
		return new DomainApiException(HttpStatus.FORBIDDEN, "AI_FEATURE_NOT_ENTITLED",
				"AI feature is not available on the current plan",
				Map.of("feature", feature.name(), "plan", plan.name(), "period", periodKey));
	}

	private static DomainApiException limitReached(
			AiFeature feature, PlanId plan, String periodKey, int used, int limit, Instant resetAt) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("feature", feature.name());
		details.put("plan", plan.name());
		details.put("period", periodKey);
		details.put("used", used);
		details.put("limit", limit);
		details.put("resetAt", resetAt);
		return new DomainApiException(HttpStatus.FORBIDDEN, "AI_USAGE_LIMIT_REACHED",
				"AI usage limit reached for " + feature.name(), details);
	}

	public record AiUsageAllowance(
			AiFeature feature,
			PlanId plan,
			String periodKey,
			int used,
			int limit,
			Instant resetAt
	) {
	}

	public record AiUsageSummary(
			AiFeature feature,
			boolean entitled,
			int used,
			int limit,
			String periodKey,
			Instant resetAt
	) {
		public int remaining() {
			return limit < 0 ? -1 : Math.max(0, limit - used);
		}
	}
}
