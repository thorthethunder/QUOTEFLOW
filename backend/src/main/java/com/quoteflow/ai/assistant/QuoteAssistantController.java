package com.quoteflow.ai.assistant;

import com.quoteflow.ai.assistant.dto.AiCapabilitiesResponse;
import com.quoteflow.ai.assistant.dto.QuoteAssistantRequest;
import com.quoteflow.ai.assistant.dto.QuoteAssistantResponse;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.provider.AiProvider;
import com.quoteflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/ai", produces = MediaType.APPLICATION_JSON_VALUE)
public class QuoteAssistantController {

	private final QuoteAssistantService quoteAssistantService;
	private final AiProperties aiProperties;
	private final AiProvider aiProvider;

	public QuoteAssistantController(
			QuoteAssistantService quoteAssistantService,
			AiProperties aiProperties,
			AiProvider aiProvider) {
		this.quoteAssistantService = quoteAssistantService;
		this.aiProperties = aiProperties;
		this.aiProvider = aiProvider;
	}

	@GetMapping("/capabilities")
	public AiCapabilitiesResponse capabilities(@AuthenticationPrincipal AuthenticatedUser principal) {
		boolean enabled = aiProperties.isEnabled();
		boolean springAi = "spring-ai".equalsIgnoreCase(
				aiProperties.getAdapter() == null ? "" : aiProperties.getAdapter().trim());
		boolean actions = enabled && aiProperties.getActions().isEnabled() && springAi;
		return new AiCapabilitiesResponse(
				enabled,
				enabled,
				enabled && springAi,
				actions,
				enabled ? aiProvider.providerName() : "DISABLED",
				enabled ? aiProvider.model() : "");
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
