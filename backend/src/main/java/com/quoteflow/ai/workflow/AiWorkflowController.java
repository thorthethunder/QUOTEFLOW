package com.quoteflow.ai.workflow;

import com.quoteflow.ai.workflow.dto.AiWorkflowListItemDto;
import com.quoteflow.ai.workflow.dto.AiWorkflowResponse;
import com.quoteflow.ai.workflow.dto.StartWorkflowRequest;
import com.quoteflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/ai/workflows", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiWorkflowController {

	private final AiWorkflowService workflowService;

	public AiWorkflowController(AiWorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@GetMapping
	public List<AiWorkflowListItemDto> list(@AuthenticationPrincipal AuthenticatedUser principal) {
		return workflowService.list(principal);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public AiWorkflowResponse start(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody StartWorkflowRequest request) {
		return workflowService.start(principal, request);
	}

	@GetMapping("/{workflowId}")
	public AiWorkflowResponse get(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID workflowId) {
		return workflowService.get(principal, workflowId);
	}

	@PostMapping("/{workflowId}/resume")
	public AiWorkflowResponse resume(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID workflowId) {
		return workflowService.resume(principal, workflowId);
	}

	@PostMapping("/{workflowId}/cancel")
	public AiWorkflowResponse cancel(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID workflowId) {
		return workflowService.cancel(principal, workflowId);
	}
}
