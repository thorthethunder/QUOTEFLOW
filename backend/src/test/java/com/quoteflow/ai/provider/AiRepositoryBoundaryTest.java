package com.quoteflow.ai.provider;

import com.quoteflow.ai.provider.ollama.OllamaAiProvider;
import com.quoteflow.ai.provider.ollama.OllamaClient;
import com.quoteflow.ai.structured.StructuredOutputValidator;
import com.quoteflow.ai.usage.LoggingAiUsageRecorder;
import com.quoteflow.customer.CustomerRepository;
import com.quoteflow.invoice.InvoiceRepository;
import com.quoteflow.payment.PaymentRepository;
import com.quoteflow.quotation.QuotationRepository;
import com.quoteflow.subscription.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiRepositoryBoundaryTest {

	private static final Set<Class<?>> FORBIDDEN = Set.of(
			CustomerRepository.class,
			QuotationRepository.class,
			InvoiceRepository.class,
			PaymentRepository.class,
			SubscriptionRepository.class);

	@Test
	void aiProviderTypesDoNotTakeBusinessRepositories() {
		List<Class<?>> aiTypes = List.of(
				DisabledAiProvider.class,
				OllamaAiProvider.class,
				OllamaClient.class,
				StructuredOutputValidator.class,
				LoggingAiUsageRecorder.class);
		for (Class<?> type : aiTypes) {
			for (Constructor<?> ctor : type.getDeclaredConstructors()) {
				for (Class<?> param : ctor.getParameterTypes()) {
					assertThat(FORBIDDEN)
							.as("%s must not depend on %s", type.getSimpleName(), param.getSimpleName())
							.doesNotContain(param);
					assertThat(param.getSimpleName())
							.as("%s parameter %s", type.getSimpleName(), param.getName())
							.doesNotEndWith("Repository");
				}
			}
		}
	}
}
