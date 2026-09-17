package com.quoteflow.ai.action;

import com.quoteflow.ai.action.dto.ActionConfirmResponse;
import com.quoteflow.ai.action.dto.ActionProposalDetailDto;
import com.quoteflow.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/ai/actions", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiActionController {

	private final AiActionApprovalService approvalService;

	public AiActionController(AiActionApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@GetMapping("/{proposalId}")
	public ActionProposalDetailDto get(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID proposalId) {
		return approvalService.get(principal, proposalId);
	}

	@PostMapping("/{proposalId}/confirm")
	public ActionConfirmResponse confirm(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID proposalId) {
		return approvalService.confirm(principal, proposalId);
	}

	@PostMapping("/{proposalId}/cancel")
	public ActionProposalDetailDto cancel(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID proposalId) {
		return approvalService.cancel(principal, proposalId);
	}
}
