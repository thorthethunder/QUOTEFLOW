package com.quoteflow.ai.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiWorkflowStepRepository extends JpaRepository<AiWorkflowStep, UUID> {

	List<AiWorkflowStep> findByWorkflowIdOrderByStepNumberAsc(UUID workflowId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select s from AiWorkflowStep s
			where s.workflowId = :workflowId
			order by s.stepNumber asc
			""")
	List<AiWorkflowStep> findByWorkflowIdForUpdate(@Param("workflowId") UUID workflowId);

	Optional<AiWorkflowStep> findByActionProposalId(UUID actionProposalId);
}
