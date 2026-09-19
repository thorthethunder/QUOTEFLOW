package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.usage.dto.AiUsageFeatureDto;
import com.quoteflow.ai.usage.dto.AiUsageSummaryResponse;
import com.quoteflow.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/ai/usage", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiUsageController {

	private static final List<AiFeature> TENANT_FEATURES = List.of(
			AiFeature.QUOTE_DRAFT,
			AiFeature.BUSINESS_COPILOT,
			AiFeature.REPORTING_INSIGHT,
			AiFeature.PAYMENT_REMINDER,
			AiFeature.KNOWLEDGE_INGESTION,
			AiFeature.KNOWLEDGE_QUERY,
			AiFeature.AGENT_WORKFLOW);

	private final AiEntitlementService aiEntitlementService;

	public AiUsageController(AiEntitlementService aiEntitlementService) {
		this.aiEntitlementService = aiEntitlementService;
	}

	@GetMapping
	public AiUsageSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser principal) {
		return new AiUsageSummaryResponse(TENANT_FEATURES.stream()
				.map(feature -> {
					var summary = aiEntitlementService.summary(principal.getBusinessId(), feature);
					return new AiUsageFeatureDto(
							feature.name(),
							summary.entitled(),
							summary.used(),
							summary.limit(),
							summary.remaining(),
							summary.periodKey(),
							summary.resetAt());
				})
				.toList());
	}
}
