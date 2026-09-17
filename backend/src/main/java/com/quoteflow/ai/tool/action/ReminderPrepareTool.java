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
 * PREPARE-ONLY reminder tool. Does NOT send email. Phase 5 handles sending.
 */
@Component
public class ReminderPrepareTool implements QuoteFlowAiTool {

	public static final String NAME = "reminder_prepare";

	private final AiActionApprovalService approvalService;

	public ReminderPrepareTool(AiActionApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String description() {
		return "Prepare a payment reminder draft for an outstanding SENT invoice for human review. "
				+ "Does NOT send email. Provide invoiceId (from invoice_search/payment_status), "
				+ "a short subject, and plain-text body. User must accept separately; sending is Phase 5.";
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
		ActionProposalSummaryDto proposal = approvalService.prepareReminder(
				context.principal(), args.invoiceId(), args.subject(), args.bodyPlainText());
		context.setActionProposal(proposal);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("proposalId", proposal.proposalId().toString());
		result.put("actionType", proposal.actionType().name());
		result.put("status", proposal.status().name());
		result.put("summary", proposal.summary());
		result.put("expiresAt", proposal.expiresAt().toString());
		result.put("emailSent", false);
		result.put("requiresHumanApproval", true);
		result.put("note", "Reminder was prepared for review only. Do not claim it was emailed.");
		return result;
	}

	public record Input(
			@NotNull UUID invoiceId,
			@NotBlank @Size(max = 200) String subject,
			@NotBlank @Size(max = 4000) String bodyPlainText
	) {
	}
}
