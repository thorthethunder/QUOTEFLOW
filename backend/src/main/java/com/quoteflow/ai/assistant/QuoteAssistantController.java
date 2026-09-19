package com.quoteflow.ai.assistant;

import com.quoteflow.ai.assistant.dto.AiCapabilitiesResponse;
import com.quoteflow.ai.assistant.dto.QuoteAssistantRequest;
import com.quoteflow.ai.assistant.dto.QuoteAssistantResponse;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiProvider;
import com.quoteflow.ai.usage.AiEntitlementService;
import com.quoteflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/v1/ai", produces = MediaType.APPLICATION_JSON_VALUE)
public class QuoteAssistantController {

	private final QuoteAssistantService quoteAssistantService;
	private final AiProperties aiProperties;
	private final AiProvider aiProvider;
	private final AiEntitlementService aiEntitlementService;

	public QuoteAssistantController(
			QuoteAssistantService quoteAssistantService,
			AiProperties aiProperties,
			AiProvider aiProvider,
			AiEntitlementService aiEntitlementService) {
		this.quoteAssistantService = quoteAssistantService;
		this.aiProperties = aiProperties;
		this.aiProvider = aiProvider;
		this.aiEntitlementService = aiEntitlementService;
	}

	@GetMapping("/capabilities")
	public AiCapabilitiesResponse capabilities(@AuthenticationPrincipal AuthenticatedUser principal) {
		boolean enabled = aiProperties.isEnabled();
		boolean springAi = "spring-ai".equalsIgnoreCase(
				aiProperties.getAdapter() == null ? "" : aiProperties.getAdapter().trim());
		boolean actions = enabled && aiProperties.getActions().isEnabled() && springAi;
		Map<String, AiCapabilitiesResponse.FeatureUsageCapability> features = Arrays.stream(new AiFeature[] {
				AiFeature.QUOTE_DRAFT,
				AiFeature.BUSINESS_COPILOT,
				AiFeature.REPORTING_INSIGHT,
				AiFeature.PAYMENT_REMINDER,
				AiFeature.KNOWLEDGE_INGESTION,
				AiFeature.KNOWLEDGE_QUERY,
				AiFeature.AGENT_WORKFLOW
		}).collect(Collectors.toMap(Enum::name, feature -> {
			var summary = aiEntitlementService.summary(principal.getBusinessId(), feature);
			return new AiCapabilitiesResponse.FeatureUsageCapability(
					summary.entitled(), summary.used(), summary.limit(), summary.remaining(),
					summary.periodKey(), summary.resetAt());
		}, (a, b) -> a, java.util.LinkedHashMap::new));
		return new AiCapabilitiesResponse(
				enabled,
				enabled,
				enabled && springAi,
				actions,
				enabled ? aiProvider.providerName() : "DISABLED",
				enabled ? aiProvider.model() : "",
				features);
	}

	/**
	 * Produces an editable quotation proposal. Does NOT persist a quotation.
	 */
	@PostMapping(path = "/quote-assistant/draft", consumes = MediaType.APPLICATION_JSON_VALUE)
	public QuoteAssistantResponse draft(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody QuoteAssistantRequest request) {
		return quoteAssistantService.draft(principal, request);
	}
}
