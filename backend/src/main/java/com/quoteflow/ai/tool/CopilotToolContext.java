package com.quoteflow.ai.tool;

import com.quoteflow.ai.action.dto.ActionProposalSummaryDto;
import com.quoteflow.ai.copilot.dto.BusinessCopilotReference;
import com.quoteflow.security.AuthenticatedUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Trusted execution context for Copilot tool calls.
 * Tenant identity comes from authentication — never from model arguments.
 */
public final class CopilotToolContext {

	public static final String TOOL_CONTEXT_KEY = "quoteflow.copilot";

	private final AuthenticatedUser principal;
	private final AtomicInteger toolCallCount = new AtomicInteger(0);
	private final int maxToolCalls;
	private final Map<String, BusinessCopilotReference> references = new LinkedHashMap<>();
	private final AtomicReference<ActionProposalSummaryDto> actionProposal = new AtomicReference<>();

	public CopilotToolContext(AuthenticatedUser principal, int maxToolCalls) {
		this.principal = Objects.requireNonNull(principal, "principal");
		this.maxToolCalls = Math.max(1, maxToolCalls);
	}

	public AuthenticatedUser principal() {
		return principal;
	}

	public int maxToolCalls() {
		return maxToolCalls;
	}

	public int incrementAndGetToolCalls() {
		int next = toolCallCount.incrementAndGet();
		if (next > maxToolCalls) {
			throw new ToolCallLimitExceededException(
					"Tool call limit of " + maxToolCalls + " exceeded for this Copilot request");
		}
		return next;
	}

	public int toolCallCount() {
		return toolCallCount.get();
	}

	public void addReference(BusinessCopilotReference reference) {
		if (reference == null || reference.id() == null || reference.type() == null) {
			return;
		}
		String key = reference.type() + ":" + reference.id();
		synchronized (references) {
			references.putIfAbsent(key, reference);
		}
	}

	public List<BusinessCopilotReference> references() {
		synchronized (references) {
			return Collections.unmodifiableList(new ArrayList<>(references.values()));
		}
	}

	public void setActionProposal(ActionProposalSummaryDto proposal) {
		actionProposal.set(proposal);
	}

	public ActionProposalSummaryDto actionProposal() {
		return actionProposal.get();
	}
}
