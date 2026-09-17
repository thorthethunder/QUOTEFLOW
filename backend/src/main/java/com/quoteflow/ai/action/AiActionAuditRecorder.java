package com.quoteflow.ai.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Structured audit for AI action proposals. No JWT/secrets/prompts/PII payloads.
 */
@Component
public class AiActionAuditRecorder {

	private static final Logger log = LoggerFactory.getLogger(AiActionAuditRecorder.class);

	public void record(
			String event,
			UUID proposalId,
			AiActionType actionType,
			UUID businessId,
			UUID userId,
			String detail) {
		log.info(
				"ai.action.audit event={} proposalId={} actionType={} businessIdPresent={} userIdPresent={} detail={}",
				event,
				proposalId,
				actionType,
				businessId != null,
				userId != null,
				detail == null ? "-" : detail);
	}
}
