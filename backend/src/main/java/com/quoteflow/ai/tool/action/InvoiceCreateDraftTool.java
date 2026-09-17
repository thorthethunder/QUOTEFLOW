package com.quoteflow.ai.tool.action;

import com.quoteflow.ai.action.AiActionApprovalService;
import com.quoteflow.ai.action.dto.ActionProposalSummaryDto;
import com.quoteflow.ai.action.payload.InvoiceCreateDraftPayload;
import com.quoteflow.ai.tool.AiToolCategory;
import com.quoteflow.ai.tool.CopilotToolContext;
import com.quoteflow.ai.tool.QuoteFlowAiTool;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PREPARE-ONLY tool. Creates a PENDING approval proposal — does not persist an invoice.
 */
@Component
public class InvoiceCreateDraftTool implements QuoteFlowAiTool {

	public static final String NAME = "invoice_create_draft";

	private final AiActionApprovalService approvalService;

	public InvoiceCreateDraftTool(AiActionApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Prepare a standalone DRAFT invoice for human approval. Does NOT create or save an invoice. "
				+ "Requires customerId (from customer_lookup) and line items. "
				+ "Does not convert quotations. User must confirm separately before anything is persisted.";
	}

	@Override
	public AiToolCategory category() {
		return AiToolCategory.ACTION_REQUIRES_APPROVAL;
	}

	@Override
	public Class<?> inputType() {
		return Input.class;
	}

	@Override
	public Object execute(Object input, CopilotToolContext context) {
		Input args = (Input) input;
		InvoiceCreateDraftPayload payload = new InvoiceCreateDraftPayload(
				args.customerId(),
				null,
				args.currency(),
				args.discountType(),
				args.discountValue(),
				args.taxRate(),
				args.notes(),
				args.terms(),
				args.items().stream()
						.map(i -> new InvoiceCreateDraftPayload.LineItem(
								i.description(), i.quantity(), i.unitPrice()))
						.toList());
		ActionProposalSummaryDto proposal = approvalService.prepareInvoiceDraft(context.principal(), payload);
		context.setActionProposal(proposal);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("proposalId", proposal.proposalId().toString());
		result.put("actionType", proposal.actionType().name());
		result.put("status", proposal.status().name());
		result.put("summary", proposal.summary());
		result.put("expiresAt", proposal.expiresAt().toString());
		result.put("persisted", false);
		result.put("requiresHumanApproval", true);
		result.put("note", "Tell the user to review and confirm. Do not claim the invoice was created.");
		return result;
	}

	public record Input(
			@NotNull UUID customerId,
			@Pattern(regexp = "^[A-Z]{3}$") String currency,
			@Size(max = 20) String discountType,
			@DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal discountValue,
			@DecimalMin("0.0") @Digits(integer = 5, fraction = 4) BigDecimal taxRate,
			@Size(max = 4000) String notes,
			@Size(max = 4000) String terms,
			@NotEmpty @Size(max = 50) List<@Valid Line> items
	) {
	}

	public record Line(
			@NotBlank @Size(max = 500) String description,
			@NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal quantity,
			@NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice
	) {
	}
}
