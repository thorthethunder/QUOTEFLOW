package com.quoteflow.ai.tool.quotation;

import com.quoteflow.ai.copilot.dto.BusinessCopilotReference;
import com.quoteflow.ai.tool.AiToolCategory;
import com.quoteflow.ai.tool.CopilotToolContext;
import com.quoteflow.ai.tool.QuoteFlowAiTool;
import com.quoteflow.ai.tool.support.CopilotPeriodResolver;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.quotation.QuotationService;
import com.quoteflow.quotation.QuotationStatus;
import com.quoteflow.quotation.dto.PagedQuotationResponse;
import com.quoteflow.quotation.dto.QuotationSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class QuotationSearchTool implements QuoteFlowAiTool {

	public static final String NAME = "quotation_search";

	private final QuotationService quotationService;
	private final CopilotPeriodResolver periodResolver;

	public QuotationSearchTool(QuotationService quotationService, CopilotPeriodResolver periodResolver) {
		this.quotationService = quotationService;
		this.periodResolver = periodResolver;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Search quotations for the authenticated business. "
				+ "Supports customer/name query, optional status (DRAFT|SENT|CANCELLED), "
				+ "and optional period (THIS_MONTH|LAST_MONTH|THIS_WEEK|TODAY) to filter by issue date. "
				+ "Use for recent quotations, counts this month, or quotations for a customer.";
	}

	@Override
	public AiToolCategory category() {
		return AiToolCategory.READ_ONLY;
	}

	@Override
	public Class<?> inputType() {
		return QuotationSearchInput.class;
	}

	@Override
	public Object execute(Object input, CopilotToolContext context) {
		QuotationSearchInput args = (QuotationSearchInput) input;
		int limit = Math.min(args.limit() == null ? 10 : args.limit(), 20);
		QuotationStatus status = parseStatus(args.status());
		String query = StringUtils.hasText(args.query()) ? args.query().trim() : null;

		PagedQuotationResponse page = quotationService.list(
				context.principal(), query, status, 0, Math.min(limit * 3, 60), "issueDate,desc");

		CopilotPeriodResolver.ResolvedPeriod period = null;
		if (StringUtils.hasText(args.period())) {
			period = periodResolver.resolve(context.principal(), args.period(), null, null);
		}

		List<Map<String, Object>> rows = new ArrayList<>();
		long matchedInPeriod = 0;
		for (QuotationSummaryResponse q : page.content()) {
			if (period != null) {
				if (q.issueDate() == null
						|| q.issueDate().isBefore(period.from())
						|| q.issueDate().isAfter(period.to())) {
					continue;
				}
				matchedInPeriod++;
			}
			if (rows.size() >= limit) {
				continue;
			}
			context.addReference(BusinessCopilotReference.quotation(q.id(), q.quotationNumber()));
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", q.id().toString());
			row.put("quotationNumber", q.quotationNumber());
			row.put("customerDisplayName", q.customerDisplayName());
			row.put("issueDate", q.issueDate() == null ? null : q.issueDate().toString());
			row.put("status", q.status() == null ? null : q.status().name());
			row.put("currency", q.currency());
			row.put("totalAmount", q.totalAmount());
			rows.add(row);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("returned", rows.size());
		result.put("bounded", true);
		result.put("totalListedWithoutPeriodFilter", page.totalElements());
		if (period != null) {
			result.put("period", period.label());
			result.put("from", period.from().toString());
			result.put("to", period.to().toString());
			result.put("timezone", period.timezone());
			result.put("matchedInPeriodSample", matchedInPeriod);
			result.put("note", "Period filter applied on a bounded recent sample; use for approximate counts in range.");
		}
		result.put("quotations", rows);
		return result;
	}

	private static QuotationStatus parseStatus(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		try {
			return QuotationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "AI_TOOL_ARGS_INVALID",
					"status must be DRAFT, SENT, or CANCELLED");
		}
	}
}
