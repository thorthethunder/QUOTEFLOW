package com.quoteflow.ai.tool.payment;

import com.quoteflow.ai.copilot.dto.BusinessCopilotReference;
import com.quoteflow.ai.tool.AiToolCategory;
import com.quoteflow.ai.tool.CopilotToolContext;
import com.quoteflow.ai.tool.QuoteFlowAiTool;
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

/**
 * Authoritative payment / outstanding balance lookups via invoice payment summaries.
 */
@Component
public class PaymentStatusTool implements QuoteFlowAiTool {

	public static final String NAME = "payment_status";

	private final InvoiceService invoiceService;

	public PaymentStatusTool(InvoiceService invoiceService) {
		this.invoiceService = invoiceService;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Authoritative payment status and outstanding balances for invoices. "
				+ "Use for 'who hasn't paid', remaining balance on an invoice number, "
				+ "or partially paid invoices. Never invent balances — values come from PaymentSummaryCalculator.";
	}

	@Override
	public AiToolCategory category() {
		return AiToolCategory.READ_ONLY;
	}

	@Override
	public Class<?> inputType() {
		return PaymentStatusInput.class;
	}

	@Override
	public Object execute(Object input, CopilotToolContext context) {
		PaymentStatusInput args = (PaymentStatusInput) input;
		int limit = Math.min(args.limit() == null ? 10 : args.limit(), 20);
		InvoicePaymentState paymentState = parsePaymentState(args.paymentState());
		String query = StringUtils.hasText(args.invoiceNumber())
				? args.invoiceNumber().trim()
				: (StringUtils.hasText(args.customerQuery()) ? args.customerQuery().trim() : null);

		if (paymentState == null && !StringUtils.hasText(args.invoiceNumber())) {
			paymentState = InvoicePaymentState.UNPAID;
		}

		PagedInvoiceResponse page = invoiceService.list(
				context.principal(),
				query,
				InvoiceStatus.SENT,
				0,
				Math.min(limit * 5, 100),
				"dueDate,asc");

		List<Map<String, Object>> rows = new ArrayList<>();
		for (InvoiceSummaryResponse inv : page.content()) {
			if (StringUtils.hasText(args.invoiceNumber())) {
				String wanted = args.invoiceNumber().trim();
				String number = inv.invoiceNumber();
				if (number == null) {
					continue;
				}
				String lower = number.toLowerCase(Locale.ROOT);
				String wantedLower = wanted.toLowerCase(Locale.ROOT);
				if (!number.equalsIgnoreCase(wanted) && !lower.contains(wantedLower)) {
					continue;
				}
			}
			if (paymentState != null && inv.paymentState() != paymentState) {
				continue;
			}
			if (rows.size() >= limit) {
				break;
			}
			context.addReference(BusinessCopilotReference.invoice(inv.id(), inv.invoiceNumber()));
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", inv.id().toString());
			row.put("invoiceNumber", inv.invoiceNumber());
			row.put("customerDisplayName", inv.customerDisplayName());
			row.put("documentStatus", inv.status() == null ? null : inv.status().name());
			row.put("paymentState", inv.paymentState() == null ? null : inv.paymentState().name());
			row.put("currency", inv.currency());
			row.put("totalAmount", inv.totalAmount());
			row.put("amountPaid", inv.amountPaid());
			row.put("balanceDue", inv.balanceDue());
			row.put("dueDate", inv.dueDate() == null ? null : inv.dueDate().toString());
			rows.add(row);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("returned", rows.size());
		result.put("bounded", true);
		result.put("paymentStateFilter", paymentState == null ? null : paymentState.name());
		result.put("invoices", rows);
		result.put("authoritative", true);
		result.put("note", "Balances are authoritative from QuoteFlow. Keep currencies separate.");
		return result;
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
