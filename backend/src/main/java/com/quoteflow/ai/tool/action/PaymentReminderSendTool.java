package com.quoteflow.ai.tool.action;

import com.quoteflow.ai.action.AiActionApprovalService;
import com.quoteflow.ai.action.dto.ActionProposalSummaryDto;
import com.quoteflow.ai.tool.AiToolCategory;
import com.quoteflow.ai.tool.CopilotToolContext;
import com.quoteflow.ai.tool.QuoteFlowAiTool;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prepare-only payment reminder SEND proposal.
 * Does not enqueue email. Recipient is resolved server-side from invoice snapshot.
 */
@Component
public class PaymentReminderSendTool implements QuoteFlowAiTool {

	public static final String NAME = "payment_reminder_send";

	private final AiActionApprovalService approvalService;

	public PaymentReminderSendTool(AiActionApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Prepare a payment reminder email for human approval and sending. "
				+ "Provide invoiceId (from invoice_search/payment_status), a short subject, and plain-text body. "
				+ "Recipient is chosen by QuoteFlow from the invoice customer email — never invent or override recipient. "
				+ "Does NOT send email until the user confirms in the UI. "
				+ "Do not invent late fees, penalties, legal threats, or credit consequences.";
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
		ActionProposalSummaryDto proposal = approvalService.preparePaymentReminderSend(
				context.principal(), args.invoiceId(), args.subject(), args.bodyPlainText());
		context.setActionProposal(proposal);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("proposalId", proposal.proposalId().toString());
		result.put("actionType", proposal.actionType().name());
		result.put("status", proposal.status().name());
		result.put("summary", proposal.summary());
		result.put("expiresAt", proposal.expiresAt().toString());
		result.put("emailQueued", false);
		result.put("requiresHumanApproval", true);
		result.put("note", "Payment reminder is ready for review. Do not claim it was emailed or queued.");
		return result;
	}

	public record Input(
			@NotNull UUID invoiceId,
			@NotBlank @Size(max = 200) String subject,
			@NotBlank @Size(max = 2000) String bodyPlainText
	) {
	}
}
