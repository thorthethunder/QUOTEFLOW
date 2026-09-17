package com.quoteflow.ai.action;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AiActionExpirationService {

	private final AiActionProposalRepository proposalRepository;

	public AiActionExpirationService(AiActionProposalRepository proposalRepository) {
		this.proposalRepository = proposalRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markExpired(UUID proposalId) {
		proposalRepository.findById(proposalId).ifPresent(p -> {
			if (p.getStatus() == AiActionProposalStatus.PENDING) {
				p.markExpired(Instant.now());
				proposalRepository.save(p);
			}
		});
	}
}
