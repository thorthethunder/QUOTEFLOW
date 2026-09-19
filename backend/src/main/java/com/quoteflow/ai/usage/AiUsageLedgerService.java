package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiProviderType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class AiUsageLedgerService {

	private final JdbcTemplate jdbcTemplate;
	private final AiCostCatalog costCatalog;
	private final Clock clock;

	public AiUsageLedgerService(JdbcTemplate jdbcTemplate, AiCostCatalog costCatalog, Clock clock) {
		this.jdbcTemplate = jdbcTemplate;
		this.costCatalog = costCatalog;
		this.clock = clock;
	}

	public void record(AiUsageEvent event) {
		Instant now = Instant.now(clock);
		String periodKey = event.billingPeriodKey() == null || event.billingPeriodKey().isBlank()
				? periodKey(now)
				: event.billingPeriodKey();
		Integer total = event.totalTokens() != null
				? event.totalTokens()
				: totalTokens(event.inputTokens(), event.outputTokens());
		AiCostEstimate cost = event.estimatedProviderCost() != null
				? new AiCostEstimate(event.estimatedProviderCost(), event.currency(), true)
				: costCatalog.estimateProviderApiCost(
						event.providerType(),
						event.providerName(),
						event.model(),
						event.usageType(),
						event.inputTokens(),
						event.outputTokens(),
						event.embeddingCount(),
						now);
		jdbcTemplate.update("""
				INSERT INTO ai_usage_events (
				  id, business_id, user_id, feature, operation, provider, model, usage_type,
				  input_tokens, output_tokens, total_tokens, input_characters, embedding_count,
				  retrieved_chunk_count, estimated_provider_cost, currency, success, error_category,
				  occurred_at, billing_period_key, reference_key
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				UUID.randomUUID(),
				event.businessId(),
				event.userId(),
				event.feature().name(),
				event.operation(),
				event.providerName() == null ? providerName(event.providerType()) : event.providerName(),
				event.model(),
				event.usageType().name(),
				event.inputTokens(),
				event.outputTokens(),
				total,
				event.inputCharacters(),
				event.embeddingCount(),
				event.retrievedChunkCount(),
				cost.amount(),
				cost.currency(),
				event.success(),
				event.errorCode(),
				Timestamp.from(now),
				periodKey,
				event.referenceKey());
	}

	public long countEvents(UUID businessId, com.quoteflow.ai.provider.AiFeature feature, AiUsageType usageType, String periodKey) {
		Long value = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM ai_usage_events
				 WHERE business_id = ? AND feature = ? AND usage_type = ? AND billing_period_key = ?
				""", Long.class, businessId, feature.name(), usageType.name(), periodKey);
		return value == null ? 0L : value;
	}

	public static String periodKey(Instant instant) {
		return DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC).format(instant);
	}

	private static Integer totalTokens(Integer input, Integer output) {
		if (input == null && output == null) {
			return null;
		}
		return (input == null ? 0 : input) + (output == null ? 0 : output);
	}

	private static String providerName(AiProviderType type) {
		return type == null ? "UNKNOWN" : type.name();
	}
}
