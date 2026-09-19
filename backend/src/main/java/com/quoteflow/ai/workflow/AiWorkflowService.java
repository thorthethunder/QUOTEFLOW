package com.quoteflow.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.action.AiActionApprovalService;
import com.quoteflow.ai.action.AiActionProposal;
import com.quoteflow.ai.action.AiActionProposalRepository;
import com.quoteflow.ai.action.AiActionProposalStatus;
import com.quoteflow.ai.action.dto.ActionProposalSummaryDto;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.usage.AiEntitlementService;
import com.quoteflow.ai.workflow.dto.AiWorkflowListItemDto;
import com.quoteflow.ai.workflow.dto.AiWorkflowResponse;
import com.quoteflow.ai.workflow.dto.AiWorkflowStepDto;
import com.quoteflow.ai.workflow.dto.StartWorkflowRequest;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.invoice.InvoiceRepository;
import com.quoteflow.invoice.OutstandingInvoiceCandidate;
import com.quoteflow.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AiWorkflowService {

	private static final String DEFAULT_GOAL = "Prepare reminders for the largest unpaid invoices.";

	private final AiWorkflowRepository workflowRepository;
	private final AiWorkflowStepRepository stepRepository;
	private final AiActionProposalRepository proposalRepository;
	private final InvoiceRepository invoiceRepository;
	private final AiActionApprovalService actionApprovalService;
	private final AiWorkflowStateMachine stateMachine;
	private final AiWorkflowStepRegistry stepRegistry;
	private final AiWorkflowRateLimiter rateLimiter;
	private final AiEntitlementService aiEntitlementService;
	private final AiProperties aiProperties;
	private final ObjectMapper objectMapper;

	public AiWorkflowService(
			AiWorkflowRepository workflowRepository,
			AiWorkflowStepRepository stepRepository,
			AiActionProposalRepository proposalRepository,
			InvoiceRepository invoiceRepository,
			AiActionApprovalService actionApprovalService,
			AiWorkflowStateMachine stateMachine,
			AiWorkflowStepRegistry stepRegistry,
			AiWorkflowRateLimiter rateLimiter,
			AiEntitlementService aiEntitlementService,
			AiProperties aiProperties,
			ObjectMapper objectMapper) {
		this.workflowRepository = workflowRepository;
		this.stepRepository = stepRepository;
		this.proposalRepository = proposalRepository;
		this.invoiceRepository = invoiceRepository;
		this.actionApprovalService = actionApprovalService;
		this.stateMachine = stateMachine;
		this.stepRegistry = stepRegistry;
		this.rateLimiter = rateLimiter;
		this.aiEntitlementService = aiEntitlementService;
		this.aiProperties = aiProperties;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public AiWorkflowResponse start(AuthenticatedUser principal, StartWorkflowRequest request) {
		requireEnabled();
		if (!rateLimiter.tryAcquire(principal.getBusinessId(), principal.getUserId())) {
			throw new DomainApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
					"Too many workflow requests. Try again shortly.");
		}
		AiWorkflowType type = parseType(request.workflowType());
		int limit = resolveLimit(request.maxItems());
		String idempotencyKey = blankToNull(request.idempotencyKey());
		if (idempotencyKey != null) {
			var existing = workflowRepository.findByBusinessIdAndRequestedByUserIdAndWorkflowTypeAndIdempotencyKey(
					principal.getBusinessId(), principal.getUserId(), type, idempotencyKey);
			if (existing.isPresent()) {
				return observeAndBuild(principal, existing.get().getId());
			}
		}
		aiEntitlementService.consumeAllowance(
				principal.getBusinessId(), principal.getUserId(), AiFeature.AGENT_WORKFLOW, type.name().toLowerCase(Locale.ROOT));

		Instant now = Instant.now();
		AiWorkflow workflow = new AiWorkflow(
				principal.getBusinessId(),
				principal.getUserId(),
				type,
				boundGoal(request.goal()),
				idempotencyKey,
				now,
				now.plus(aiProperties.getWorkflows().getTtl()));
		try {
			workflow = workflowRepository.saveAndFlush(workflow);
		} catch (DataIntegrityViolationException ex) {
			if (idempotencyKey != null) {
				return observeAndBuild(principal, workflowRepository
						.findByBusinessIdAndRequestedByUserIdAndWorkflowTypeAndIdempotencyKey(
								principal.getBusinessId(), principal.getUserId(), type, idempotencyKey)
						.orElseThrow(() -> ex).getId());
			}
			throw ex;
		}

		stateMachine.transition(workflow, AiWorkflowStatus.RUNNING);
		if (type == AiWorkflowType.PAYMENT_FOLLOW_UP) {
			runPaymentFollowUpPlan(principal, workflow, limit);
		}
		return buildResponse(workflow, stepRepository.findByWorkflowIdOrderByStepNumberAsc(workflow.getId()));
	}

	@Transactional(readOnly = true)
	public List<AiWorkflowListItemDto> list(AuthenticatedUser principal) {
		return workflowRepository.findVisible(principal.getBusinessId(), principal.getUserId(), PageRequest.of(0, 20))
				.map(w -> new AiWorkflowListItemDto(
						w.getId(), w.getWorkflowType(), w.getStatus(), w.getGoal(), w.getUpdatedAt(), w.getExpiresAt()))
				.toList();
	}

	@Transactional
	public AiWorkflowResponse get(AuthenticatedUser principal, UUID workflowId) {
		return observeAndBuild(principal, workflowId);
	}

	@Transactional
	public AiWorkflowResponse resume(AuthenticatedUser principal, UUID workflowId) {
		return observeAndBuild(principal, workflowId);
	}

	@Transactional
	public AiWorkflowResponse cancel(AuthenticatedUser principal, UUID workflowId) {
		AiWorkflow workflow = loadForUpdate(principal, workflowId);
		List<AiWorkflowStep> steps = stepRepository.findByWorkflowIdForUpdate(workflowId);
		if (workflow.isExpired(Instant.now())) {
			markExpired(workflow, steps);
			return buildResponse(workflow, steps);
		}
		stateMachine.transition(workflow, AiWorkflowStatus.CANCELLED);
		for (AiWorkflowStep step : steps) {
			if (step.getStatus() == AiWorkflowStepStatus.PENDING
					|| step.getStatus() == AiWorkflowStepStatus.RUNNING
					|| step.getStatus() == AiWorkflowStepStatus.WAITING_FOR_APPROVAL) {
				stateMachine.transition(step, AiWorkflowStepStatus.CANCELLED);
				step.setFailureCode("WORKFLOW_CANCELLED");
				if (step.getActionProposalId() != null) {
					proposalRepository.findByIdAndBusinessIdForUpdate(step.getActionProposalId(), workflow.getBusinessId())
							.filter(p -> p.getStatus() == AiActionProposalStatus.PENDING)
							.ifPresent(p -> actionApprovalService.cancel(principal, p.getId()));
				}
			}
		}
		return buildResponse(workflow, steps);
	}

	private void runPaymentFollowUpPlan(AuthenticatedUser principal, AiWorkflow workflow, int limit) {
		AiWorkflowStep readStep = newStep(workflow, 1, AiWorkflowStepType.READ_OUTSTANDING_INVOICES,
				Map.of("limit", limit, "orderBy", "balanceDue DESC, dueDate NULLS LAST, invoiceNumber ASC"));
		stateMachine.transition(readStep, AiWorkflowStepStatus.RUNNING);
		List<OutstandingInvoiceCandidate> candidates =
				invoiceRepository.findTopOutstandingForPaymentFollowUp(workflow.getBusinessId(), limit);
		readStep.setOutputJson(json(Map.of(
				"selectedCount", candidates.size(),
				"invoices", candidates.stream().map(this::candidateJson).toList())));
		stateMachine.transition(readStep, AiWorkflowStepStatus.COMPLETED);
		stepRepository.save(readStep);

		int stepNumber = 2;
		int actionCount = 0;
		for (OutstandingInvoiceCandidate candidate : candidates) {
			if (actionCount >= aiProperties.getWorkflows().getMaxActionProposals()) {
				break;
			}
			AiWorkflowStep prepare = newStep(workflow, stepNumber++, AiWorkflowStepType.PREPARE_PAYMENT_REMINDER,
					Map.of("invoiceId", candidate.getId(), "invoiceNumber", candidate.getInvoiceNumber()));
			stateMachine.transition(prepare, AiWorkflowStepStatus.RUNNING);
			try {
				ActionProposalSummaryDto proposal = actionApprovalService.preparePaymentReminderSend(
						principal,
						candidate.getId(),
						"Payment reminder for " + candidate.getInvoiceNumber(),
						reminderBody(candidate));
				prepare.setActionProposalId(proposal.proposalId());
				prepare.setOutputJson(json(Map.of(
						"proposalId", proposal.proposalId(),
						"status", proposal.status().name(),
						"confirmButtonLabel", proposal.confirmButtonLabel())));
				stateMachine.transition(prepare, AiWorkflowStepStatus.WAITING_FOR_APPROVAL);
				stepRepository.save(prepare);

				AiWorkflowStep observe = newStep(workflow, stepNumber++, AiWorkflowStepType.OBSERVE_ACTION_RESULT,
						Map.of("proposalId", proposal.proposalId()));
				stepRepository.save(observe);
				actionCount++;
			} catch (DomainApiException ex) {
				prepare.setFailureCode(ex.getCode());
				prepare.setOutputJson(json(Map.of("errorCode", ex.getCode())));
				stateMachine.transition(prepare, AiWorkflowStepStatus.FAILED);
				stepRepository.save(prepare);
			}
		}
		List<AiWorkflowStep> steps = stepRepository.findByWorkflowIdOrderByStepNumberAsc(workflow.getId());
		refreshWorkflowStatus(workflow, steps);
	}

	private AiWorkflowResponse observeAndBuild(AuthenticatedUser principal, UUID workflowId) {
		AiWorkflow workflow = loadForUpdate(principal, workflowId);
		List<AiWorkflowStep> steps = stepRepository.findByWorkflowIdForUpdate(workflowId);
		if (workflow.isExpired(Instant.now())) {
			markExpired(workflow, steps);
		} else if (workflow.getStatus() != AiWorkflowStatus.CANCELLED) {
			observeProposalStates(workflow, steps);
			refreshWorkflowStatus(workflow, steps);
		}
		return buildResponse(workflow, steps);
	}

	private void observeProposalStates(AiWorkflow workflow, List<AiWorkflowStep> steps) {
		Map<UUID, AiActionProposal> proposals = new LinkedHashMap<>();
		for (AiWorkflowStep step : steps) {
			if (step.getActionProposalId() != null) {
				proposalRepository.findByIdAndBusinessId(step.getActionProposalId(), workflow.getBusinessId())
						.ifPresent(p -> proposals.put(p.getId(), p));
				continue;
			}
			if (step.getStepType() == AiWorkflowStepType.OBSERVE_ACTION_RESULT) {
				UUID proposalId = readProposalId(step);
				if (proposalId != null) {
					proposalRepository.findByIdAndBusinessId(proposalId, workflow.getBusinessId())
							.ifPresent(p -> proposals.put(p.getId(), p));
				}
			}
		}
		for (AiWorkflowStep step : steps) {
			UUID proposalId = step.getActionProposalId() != null ? step.getActionProposalId() : readProposalId(step);
			if (proposalId == null || isTerminal(step.getStatus())) {
				continue;
			}
			AiActionProposal proposal = proposals.get(proposalId);
			if (proposal == null) {
				continue;
			}
			applyProposalStatus(step, proposal);
		}
	}

	private void applyProposalStatus(AiWorkflowStep step, AiActionProposal proposal) {
		Map<String, Object> output = new LinkedHashMap<>();
		output.put("proposalId", proposal.getId());
		output.put("proposalStatus", proposal.getStatus().name());
		output.put("resultReferenceType", proposal.getResultReferenceType());
		output.put("resultReferenceId", proposal.getResultReferenceId());
		step.setOutputJson(json(output));
		switch (proposal.getStatus()) {
			case EXECUTED -> {
				if (step.getStatus() == AiWorkflowStepStatus.PENDING) {
					stateMachine.transition(step, AiWorkflowStepStatus.RUNNING);
				}
				stateMachine.transition(step, AiWorkflowStepStatus.COMPLETED);
			}
			case CANCELLED -> {
				step.setFailureCode("ACTION_CANCELLED");
				stateMachine.transition(step, AiWorkflowStepStatus.CANCELLED);
			}
			case EXPIRED -> {
				step.setFailureCode("ACTION_EXPIRED");
				stateMachine.transition(step, AiWorkflowStepStatus.EXPIRED);
			}
			case FAILED -> {
				step.setFailureCode(proposal.getFailureCode() == null ? "ACTION_FAILED" : proposal.getFailureCode());
				stateMachine.transition(step, AiWorkflowStepStatus.FAILED);
			}
			case PENDING -> {
				if (step.getStatus() == AiWorkflowStepStatus.PENDING) {
					stateMachine.transition(step, AiWorkflowStepStatus.RUNNING);
					stateMachine.transition(step, AiWorkflowStepStatus.WAITING_FOR_APPROVAL);
				}
			}
		}
	}

	private void refreshWorkflowStatus(AiWorkflow workflow, List<AiWorkflowStep> steps) {
		if (workflow.getStatus() == AiWorkflowStatus.CANCELLED || workflow.getStatus() == AiWorkflowStatus.EXPIRED) {
			return;
		}
		long waiting = steps.stream().filter(s -> s.getStatus() == AiWorkflowStepStatus.WAITING_FOR_APPROVAL).count();
		long pending = steps.stream().filter(s -> s.getStatus() == AiWorkflowStepStatus.PENDING
				|| s.getStatus() == AiWorkflowStepStatus.RUNNING).count();
		long completedActions = steps.stream().filter(s -> s.getStepType() == AiWorkflowStepType.OBSERVE_ACTION_RESULT
				&& s.getStatus() == AiWorkflowStepStatus.COMPLETED).count();
		long nonCompletedActions = steps.stream().filter(s -> s.getStepType() == AiWorkflowStepType.OBSERVE_ACTION_RESULT
				&& (s.getStatus() == AiWorkflowStepStatus.FAILED
				|| s.getStatus() == AiWorkflowStepStatus.CANCELLED
				|| s.getStatus() == AiWorkflowStepStatus.EXPIRED)).count();
		if (waiting > 0 || pending > 0) {
			stateMachine.transition(workflow, AiWorkflowStatus.WAITING_FOR_APPROVAL);
		} else if (completedActions > 0 && nonCompletedActions > 0) {
			stateMachine.transition(workflow, AiWorkflowStatus.COMPLETED_WITH_PARTIAL_RESULTS);
		} else if (nonCompletedActions > 0) {
			stateMachine.transition(workflow, AiWorkflowStatus.COMPLETED_WITH_PARTIAL_RESULTS);
		} else {
			stateMachine.transition(workflow, AiWorkflowStatus.COMPLETED);
		}
	}

	private void markExpired(AiWorkflow workflow, List<AiWorkflowStep> steps) {
		stateMachine.transition(workflow, AiWorkflowStatus.EXPIRED);
		for (AiWorkflowStep step : steps) {
			if (!isTerminal(step.getStatus())) {
				step.setFailureCode("WORKFLOW_EXPIRED");
				stateMachine.transition(step, AiWorkflowStepStatus.EXPIRED);
			}
		}
	}

	private AiWorkflow loadForUpdate(AuthenticatedUser principal, UUID workflowId) {
		return workflowRepository
				.findVisibleByIdForUpdate(workflowId, principal.getBusinessId(), principal.getUserId())
				.orElseThrow(() -> new DomainApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workflow not found"));
	}

	private AiWorkflowStep newStep(AiWorkflow workflow, int stepNumber, AiWorkflowStepType type, Map<String, ?> input) {
		if (!stepRegistry.isAllowed(type)) {
			throw new DomainApiException(HttpStatus.FORBIDDEN, "WORKFLOW_STEP_FORBIDDEN",
					"Workflow step is not allowed");
		}
		if (stepNumber > aiProperties.getWorkflows().getMaxSteps()) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_LIMIT_EXCEEDED",
					"Workflow exceeds configured maximum step count");
		}
		return new AiWorkflowStep(workflow.getId(), stepNumber, type, stepRegistry.classification(type), json(input));
	}

	private AiWorkflowResponse buildResponse(AiWorkflow workflow, List<AiWorkflowStep> steps) {
		List<AiWorkflowStepDto> stepDtos = steps.stream().map(this::toDto).toList();
		int pending = (int) steps.stream().filter(s -> s.getStatus() == AiWorkflowStepStatus.WAITING_FOR_APPROVAL
				&& s.getActionProposalId() != null).count();
		int executed = (int) steps.stream().filter(s -> s.getStepType() == AiWorkflowStepType.OBSERVE_ACTION_RESULT
				&& s.getStatus() == AiWorkflowStepStatus.COMPLETED).count();
		int failed = (int) steps.stream().filter(s -> s.getStepType() == AiWorkflowStepType.OBSERVE_ACTION_RESULT
				&& s.getStatus() == AiWorkflowStepStatus.FAILED).count();
		int cancelled = (int) steps.stream().filter(s -> s.getStepType() == AiWorkflowStepType.OBSERVE_ACTION_RESULT
				&& s.getStatus() == AiWorkflowStepStatus.CANCELLED).count();
		int expired = (int) steps.stream().filter(s -> s.getStepType() == AiWorkflowStepType.OBSERVE_ACTION_RESULT
				&& s.getStatus() == AiWorkflowStepStatus.EXPIRED).count();
		return new AiWorkflowResponse(
				workflow.getId(),
				workflow.getWorkflowType(),
				workflow.getStatus(),
				workflow.getGoal(),
				workflow.getCreatedAt(),
				workflow.getUpdatedAt(),
				workflow.getStartedAt(),
				workflow.getCompletedAt(),
				workflow.getExpiresAt(),
				stepDtos,
				new AiWorkflowResponse.WorkflowSummary(
						steps.size(), pending, executed, failed, cancelled, expired,
						workflow.getStatus() == AiWorkflowStatus.COMPLETED_WITH_PARTIAL_RESULTS));
	}

	private AiWorkflowStepDto toDto(AiWorkflowStep step) {
		return new AiWorkflowStepDto(
				step.getId(),
				step.getStepNumber(),
				step.getStepType(),
				step.getClassification(),
				step.getStatus(),
				step.getActionProposalId(),
				readTree(step.getInputJson()),
				readTree(step.getOutputJson()),
				step.getStartedAt(),
				step.getCompletedAt(),
				step.getFailureCode());
	}

	private Map<String, Object> candidateJson(OutstandingInvoiceCandidate candidate) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("invoiceId", candidate.getId());
		map.put("invoiceNumber", candidate.getInvoiceNumber());
		map.put("customerDisplayName", candidate.getCustomerDisplayName());
		map.put("currency", candidate.getCurrency());
		map.put("dueDate", candidate.getDueDate());
		map.put("balanceDue", candidate.getBalanceDue());
		return map;
	}

	private String reminderBody(OutstandingInvoiceCandidate candidate) {
		String balance = formatMoney(candidate.getBalanceDue(), candidate.getCurrency());
		List<String> lines = new ArrayList<>();
		lines.add("Hello " + safeName(candidate.getCustomerDisplayName()) + ",");
		lines.add("");
		lines.add("This is a reminder that invoice " + candidate.getInvoiceNumber()
				+ " has an outstanding balance of " + balance + ".");
		if (candidate.getDueDate() != null) {
			lines.add("The due date on record is " + candidate.getDueDate() + ".");
		}
		lines.add("Please arrange payment when convenient or contact us if you have questions.");
		return String.join("\n", lines);
	}

	private UUID readProposalId(AiWorkflowStep step) {
		try {
			JsonNode input = objectMapper.readTree(step.getInputJson());
			if (input.hasNonNull("proposalId")) {
				return UUID.fromString(input.get("proposalId").asText());
			}
			return null;
		} catch (Exception ex) {
			return null;
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to serialize workflow state", ex);
		}
	}

	private JsonNode readTree(String json) {
		if (!StringUtils.hasText(json)) {
			return null;
		}
		try {
			return objectMapper.readTree(json);
		} catch (Exception ex) {
			return objectMapper.createObjectNode();
		}
	}

	private void requireEnabled() {
		if (!aiProperties.getWorkflows().isEnabled()) {
			throw new DomainApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_WORKFLOWS_DISABLED",
					"AI workflows are disabled");
		}
		actionApprovalService.requireActionsEnabled();
	}

	private AiWorkflowType parseType(String value) {
		try {
			return AiWorkflowType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
		} catch (Exception ex) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_WORKFLOW_TYPE",
					"Unsupported workflow type");
		}
	}

	private int resolveLimit(Integer requested) {
		int max = aiProperties.getWorkflows().getMaxPaymentFollowUpItems();
		int value = requested == null ? max : requested;
		if (value < 1 || value > max || value > AiProperties.Workflows.HARD_MAX_PAYMENT_FOLLOW_UP_ITEMS) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_LIMIT_EXCEEDED",
					"Payment follow-up workflows can prepare between 1 and " + max + " reminders.");
		}
		return value;
	}

	private static boolean isTerminal(AiWorkflowStepStatus status) {
		return status == AiWorkflowStepStatus.COMPLETED
				|| status == AiWorkflowStepStatus.FAILED
				|| status == AiWorkflowStepStatus.CANCELLED
				|| status == AiWorkflowStepStatus.SKIPPED
				|| status == AiWorkflowStepStatus.EXPIRED;
	}

	private static String blankToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private static String boundGoal(String goal) {
		if (!StringUtils.hasText(goal)) {
			return DEFAULT_GOAL;
		}
		String trimmed = goal.trim();
		return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
	}

	private static String safeName(String name) {
		return StringUtils.hasText(name) ? name.trim() : "there";
	}

	private static String formatMoney(BigDecimal amount, String currencyCode) {
		try {
			NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
			format.setCurrency(Currency.getInstance(currencyCode == null ? "INR" : currencyCode));
			return format.format(amount == null ? BigDecimal.ZERO : amount);
		} catch (Exception ex) {
			return (amount == null ? "0.00" : amount.toPlainString()) + " " + (currencyCode == null ? "" : currencyCode);
		}
	}
}
