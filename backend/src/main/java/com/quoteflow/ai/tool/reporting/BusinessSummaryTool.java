package com.quoteflow.ai.tool.reporting;

import com.quoteflow.ai.tool.AiToolCategory;
import com.quoteflow.ai.tool.CopilotToolContext;
import com.quoteflow.ai.tool.QuoteFlowAiTool;
import com.quoteflow.ai.tool.support.CopilotPeriodResolver;
import com.quoteflow.reporting.ReportingService;
import com.quoteflow.reporting.dto.DashboardSummaryResponse;
import com.quoteflow.reporting.dto.MoneyByCurrency;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dashboard/reporting summary via existing ReportingService semantics.
 * Multi-currency amounts remain separated (no FX summing).
 */
@Component
public class BusinessSummaryTool implements QuoteFlowAiTool {

	public static final String NAME = "business_summary";

	private final ReportingService reportingService;
	private final CopilotPeriodResolver periodResolver;

	public BusinessSummaryTool(ReportingService reportingService, CopilotPeriodResolver periodResolver) {
		this.reportingService = reportingService;
		this.periodResolver = periodResolver;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Business dashboard summary for the authenticated tenant: quotations created, "
				+ "invoiced amounts, collected payments, and outstanding balances for a period "
				+ "(THIS_MONTH default, LAST_MONTH, THIS_WEEK, TODAY, or CUSTOM with from/to). "
				+ "Amounts are MoneyByCurrency arrays — NEVER sum different currencies into one total.";
	}

	@Override
	public AiToolCategory category() {
		return AiToolCategory.READ_ONLY;
	}

	@Override
	public Class<?> inputType() {
		return BusinessSummaryInput.class;
	}

	@Override
	public Object execute(Object input, CopilotToolContext context) {
		BusinessSummaryInput args = (BusinessSummaryInput) input;
		String period = StringUtils.hasText(args.period()) ? args.period() : "THIS_MONTH";
		CopilotPeriodResolver.ResolvedPeriod resolved =
				periodResolver.resolve(context.principal(), period, args.from(), args.to());

		DashboardSummaryResponse summary = reportingService.summary(
				context.principal(), resolved.from(), resolved.to());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("period", resolved.label());
		result.put("from", summary.from().toString());
		result.put("to", summary.to().toString());
		result.put("timezone", summary.timezone());

		Map<String, Object> quotations = new LinkedHashMap<>();
		long createdCount = summary.quotations().draftCount()
				+ summary.quotations().sentCount()
				+ summary.quotations().cancelledCount();
		quotations.put("createdCount", createdCount);
		quotations.put("draftCount", summary.quotations().draftCount());
		quotations.put("sentCount", summary.quotations().sentCount());
		quotations.put("cancelledCount", summary.quotations().cancelledCount());
		quotations.put("convertedCount", summary.quotations().convertedCount());
		quotations.put("quotedAmountByCurrency", money(summary.quotations().quotedAmountByCurrency()));
		result.put("quotations", quotations);

		Map<String, Object> invoices = new LinkedHashMap<>();
		invoices.put("sentCount", summary.invoices().sentCount());
		invoices.put("unpaidCount", summary.invoices().unpaidCount());
		invoices.put("partiallyPaidCount", summary.invoices().partiallyPaidCount());
		invoices.put("paidCount", summary.invoices().paidCount());
		invoices.put("invoicedAmountByCurrency", money(summary.invoices().invoicedAmountByCurrency()));
		invoices.put("outstandingAmountByCurrency", money(summary.invoices().outstandingAmountByCurrency()));
		result.put("invoices", invoices);

		Map<String, Object> payments = new LinkedHashMap<>();
		payments.put("recordedCount", summary.payments().recordedCount());
		payments.put("collectedAmountByCurrency", money(summary.payments().collectedAmountByCurrency()));
		result.put("payments", payments);

		result.put("multiCurrencyRule",
				"Never combine different currencies into one authoritative total. QuoteFlow has no FX conversion.");
		result.put("authoritative", true);
		return result;
	}

	private static List<Map<String, Object>> money(List<MoneyByCurrency> amounts) {
		if (amounts == null) {
			return List.of();
		}
		return amounts.stream().map(m -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("currency", m.currency());
			row.put("amount", m.amount());
			return row;
		}).collect(Collectors.toList());
	}
}
