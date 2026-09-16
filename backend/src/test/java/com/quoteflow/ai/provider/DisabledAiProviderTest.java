package com.quoteflow.ai.provider;

import com.quoteflow.ai.exception.AiUnavailableException;
import com.quoteflow.ai.structured.StructuredAiRequest;
import com.quoteflow.ai.structured.demo.QuotationDraftProposal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledAiProviderTest {

	private final DisabledAiProvider provider = new DisabledAiProvider();

	@Test
	void reportsDisabled() {
		assertThat(provider.isEnabled()).isFalse();
		assertThat(provider.isAvailable()).isFalse();
		assertThat(provider.type()).isEqualTo(AiProviderType.DISABLED);
	}

	@Test
	void generateThrowsUnavailable() {
		assertThatThrownBy(() -> provider.generate(AiRequest.of(AiFeature.PROVIDER_SMOKE, "hi")))
				.isInstanceOf(AiUnavailableException.class);
	}

	@Test
	void structuredThrowsUnavailable() {
		ObjectMapper mapper = new ObjectMapper();
		var request = new StructuredAiRequest<>(
				null,
				"draft",
				QuotationDraftProposal.class,
				QuotationDraftProposal.jsonSchema(mapper),
				AiFeature.STRUCTURED_SMOKE,
				AiGenerationOptions.structuredExtraction(),
				null,
				null);
		assertThatThrownBy(() -> provider.generateStructured(request))
				.isInstanceOf(AiUnavailableException.class);
	}
}
