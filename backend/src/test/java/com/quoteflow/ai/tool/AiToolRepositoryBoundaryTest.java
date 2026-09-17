package com.quoteflow.ai.tool;

import com.quoteflow.ai.tool.customer.CustomerLookupTool;
import com.quoteflow.ai.tool.invoice.InvoiceSearchTool;
import com.quoteflow.ai.tool.payment.PaymentStatusTool;
import com.quoteflow.ai.tool.quotation.QuotationSearchTool;
import com.quoteflow.ai.tool.reporting.BusinessSummaryTool;
import com.quoteflow.customer.CustomerRepository;
import com.quoteflow.invoice.InvoiceRepository;
import com.quoteflow.payment.PaymentRepository;
import com.quoteflow.quotation.QuotationRepository;
import com.quoteflow.subscription.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiToolRepositoryBoundaryTest {

	private static final Set<Class<?>> FORBIDDEN = Set.of(
			CustomerRepository.class,
			QuotationRepository.class,
			InvoiceRepository.class,
			PaymentRepository.class,
			SubscriptionRepository.class);

	@Test
	void toolsDoNotDependOnRepositoriesDirectly() {
		List<Class<?>> types = List.of(
				CustomerLookupTool.class,
				QuotationSearchTool.class,
				InvoiceSearchTool.class,
				PaymentStatusTool.class,
				BusinessSummaryTool.class,
				AiToolRegistry.class,
				CopilotToolContext.class);
		for (Class<?> type : types) {
			for (Constructor<?> ctor : type.getDeclaredConstructors()) {
				for (Class<?> param : ctor.getParameterTypes()) {
					assertThat(FORBIDDEN)
							.as("%s must not take %s", type.getSimpleName(), param.getSimpleName())
							.doesNotContain(param);
					assertThat(param.getSimpleName())
							.as("%s ctor param %s", type.getSimpleName(), param.getName())
							.doesNotEndWith("Repository");
				}
			}
			for (Field field : type.getDeclaredFields()) {
				assertThat(FORBIDDEN)
						.as("%s field %s", type.getSimpleName(), field.getName())
						.doesNotContain(field.getType());
				assertThat(field.getType().getSimpleName())
						.as("%s field type %s", type.getSimpleName(), field.getName())
						.doesNotEndWith("Repository");
			}
		}
	}

	@Test
	void onlyReadOnlyCategoryIsExecutablePolicy() {
		assertThat(AiToolCategory.READ_ONLY.name()).isEqualTo("READ_ONLY");
		assertThat(AiToolCategory.ACTION_REQUIRES_APPROVAL.name()).isEqualTo("ACTION_REQUIRES_APPROVAL");
		assertThat(AiToolCategory.FORBIDDEN.name()).isEqualTo("FORBIDDEN");
	}
}
