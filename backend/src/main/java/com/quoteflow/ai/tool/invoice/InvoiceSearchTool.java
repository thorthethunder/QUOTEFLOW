package com.quoteflow.ai.tool.invoice;

import com.quoteflow.ai.copilot.dto.BusinessCopilotReference;
import com.quoteflow.ai.tool.AiToolCategory;
import com.quoteflow.ai.tool.CopilotToolContext;
import com.quoteflow.ai.tool.QuoteFlowAiTool;
import com.quoteflow.ai.tool.support.CopilotPeriodResolver;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.invoice.InvoiceService;
import com.quoteflow.invoice.InvoiceStatus;
import com.quoteflow.invoice.dto.InvoiceSummaryResponse;
import com.quoteflow.invoice.dto.PagedInvoiceResponse;
import com.quoteflow.payment.InvoicePaymentState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class InvoiceSearchTool implements QuoteFlowAiTool {

	public static final String NAME = "invoice_search";

	private final InvoiceService invoiceService;
	private final CopilotPeriodResolver periodResolver;

	public InvoiceSearchTool(InvoiceService invoiceService, CopilotPeriodResolver periodResolver) {
		this.invoiceService = invoiceService;
		this.periodResolver = periodResolver;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Search invoices for the authenticated business. "
				+ "documentStatus is DRAFT|SENT|CANCELLED (document lifecycle). "
				+ "paymentState is UNPAID|PARTIALLY_PAID|PAID (authoritative payment summary — distinct from document status). "
				+ "Use for unpaid invoices, partially paid invoices, invoices for a customer, or recent invoices.";
	}

	@Override
	public AiToolCategory category() {
		return AiToolCategory.READ_ONLY;
	}

	@Override
	public Class<?> inputType() {
		return InvoiceSearchInput.class;
	}

	@Override
	public Object execute(Object input, CopilotToolContext context) {
		InvoiceSearchInput args = (InvoiceSearchInput) input;
		int limit = Math.min(args.limit() == null ? 10 : args.limit(), 20);
		InvoiceStatus documentStatus = parseDocumentStatus(args.documentStatus());
		InvoicePaymentState paymentState = parsePaymentState(args.paymentState());
		String query = StringUtils.hasText(args.query()) ? args.query().trim() : null;

		// Default unpaid/partial questions to SENT documents when document status omitted.
		if (documentStatus == null && paymentState != null
				&& (paymentState == InvoicePaymentState.UNPAID || paymentState == InvoicePaymentState.PARTIALLY_PAID)) {
			documentStatus = InvoiceStatus.SENT;
		}

		PagedInvoiceResponse page = invoiceService.list(
				context.principal(),
				query,
				documentStatus,
				0,
				Math.min(limit * 5, 100),
				"issueDate,desc");

		CopilotPeriodResolver.ResolvedPeriod period = null;
		if (StringUtils.hasText(args.period())) {
			period = periodResolver.resolve(context.principal(), args.period(), null, null);
		}

		List<Map<String, Object>> rows = new ArrayList<>();
		for (InvoiceSummaryResponse inv : page.content()) {
			if (paymentState != null && inv.paymentState() != paymentState) {
				continue;
			}
			if (period != null) {
				if (inv.issueDate() == null
						|| inv.issueDate().isBefore(period.from())
						|| inv.issueDate().isAfter(period.to())) {
					continue;
				}
			}
			if (rows.size() >= limit) {
				break;
			}
			context.addReference(BusinessCopilotReference.invoice(inv.id(), inv.invoiceNumber()));
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", inv.id().toString());
			row.put("invoiceNumber", inv.invoiceNumber());
			row.put("customerDisplayName", inv.customerDisplayName());
			row.put("issueDate", inv.issueDate() == null ? null : inv.issueDate().toString());
			row.put("dueDate", inv.dueDate() == null ? null : inv.dueDate().toString());
			row.put("documentStatus", inv.status() == null ? null : inv.status().name());
			row.put("paymentState", inv.paymentState() == null ? null : inv.paymentState().name());
			row.put("currency", inv.currency());
			row.put("totalAmount", inv.totalAmount());
			row.put("amountPaid", inv.amountPaid());
			row.put("balanceDue", inv.balanceDue());
			rows.add(row);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("returned", rows.size());
		result.put("bounded", true);
		result.put("documentStatusFilter", documentStatus == null ? null : documentStatus.name());
		result.put("paymentStateFilter", paymentState == null ? null : paymentState.name());
		if (period != null) {
			result.put("period", period.label());
			result.put("from", period.from().toString());
			result.put("to", period.to().toString());
			result.put("timezone", period.timezone());
		}
		result.put("invoices", rows);
		result.put("note",
				"paymentState comes from QuoteFlow PaymentSummaryCalculator; do not recalculate balances.");
		return result;
	}

	private static InvoiceStatus parseDocumentStatus(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		try {
			return InvoiceStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "AI_TOOL_ARGS_INVALID",
					"documentStatus must be DRAFT, SENT, or CANCELLED");
		}
	}

	private static InvoicePaymentState parsePaymentState(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		try {
			return InvoicePaymentState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "AI_TOOL_ARGS_INVALID",
					"paymentState must be UNPAID, PARTIALLY_PAID, or PAID");
		}
	}
}
