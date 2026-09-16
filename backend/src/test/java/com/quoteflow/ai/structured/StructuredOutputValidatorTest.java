package com.quoteflow.ai.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.exception.AiInvalidResponseException;
import com.quoteflow.ai.structured.demo.QuotationDraftProposal;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputValidatorTest {

	private StructuredOutputValidator validator;

	@BeforeEach
	void setUp() {
		Validator beanValidator = Validation.buildDefaultValidatorFactory().getValidator();
		validator = new StructuredOutputValidator(new ObjectMapper(), beanValidator, 10_000);
	}

	@Test
	void acceptsValidQuotationDraftJson() {
		String json = """
				{
				  "customerName": "Raj Electrical",
				  "items": [
				    {"description": "ceiling fans", "quantity": 2, "unitPrice": 3000},
				    {"description": "switches", "quantity": 5, "unitPrice": 250}
				  ],
				  "notes": "smoke"
				}
				""";
		QuotationDraftProposal draft = validator.validateAndParse(json, QuotationDraftProposal.class);
		assertThat(draft.customerName()).isEqualTo("Raj Electrical");
		assertThat(draft.items()).hasSize(2);
	}

	@Test
	void rejectsMalformedJson() {
		assertThatThrownBy(() -> validator.validateAndParse("{not-json", QuotationDraftProposal.class))
				.isInstanceOf(AiInvalidResponseException.class)
				.hasMessageContaining("not valid JSON");
	}

	@Test
	void rejectsEmptyResponse() {
		assertThatThrownBy(() -> validator.validateAndParse("   ", QuotationDraftProposal.class))
				.isInstanceOf(AiInvalidResponseException.class)
				.hasMessageContaining("empty");
	}

	@Test
	void rejectsSchemaMismatch() {
		assertThatThrownBy(() -> validator.validateAndParse("{\"customerName\":\"x\",\"items\":[]}", QuotationDraftProposal.class))
				.isInstanceOf(AiInvalidResponseException.class);
	}

	@Test
	void rejectsOversizedResponse() {
		StructuredOutputValidator tiny = new StructuredOutputValidator(
				new ObjectMapper(),
				Validation.buildDefaultValidatorFactory().getValidator(),
				20);
		assertThatThrownBy(() -> tiny.validateAndParse("{\"customerName\":\"too-long-for-limit\"}", QuotationDraftProposal.class))
				.isInstanceOf(AiInvalidResponseException.class)
				.hasMessageContaining("max size");
	}
}
