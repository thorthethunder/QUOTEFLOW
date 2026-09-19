package com.quoteflow.ai.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface AiWorkflowRepository extends JpaRepository<AiWorkflow, UUID> {

	@Query("""
			select w from AiWorkflow w
			where w.businessId = :businessId
			  and w.requestedByUserId = :userId
			order by w.updatedAt desc
			""")
	Page<AiWorkflow> findVisible(
			@Param("businessId") UUID businessId,
			@Param("userId") UUID userId,
			Pageable pageable);

	@Query("""
			select w from AiWorkflow w
			where w.id = :id
			  and w.businessId = :businessId
			  and w.requestedByUserId = :userId
			""")
	Optional<AiWorkflow> findVisibleById(
			@Param("id") UUID id,
			@Param("businessId") UUID businessId,
			@Param("userId") UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select w from AiWorkflow w
			where w.id = :id
			  and w.businessId = :businessId
			  and w.requestedByUserId = :userId
			""")
	Optional<AiWorkflow> findVisibleByIdForUpdate(
			@Param("id") UUID id,
			@Param("businessId") UUID businessId,
			@Param("userId") UUID userId);

	Optional<AiWorkflow> findByBusinessIdAndRequestedByUserIdAndWorkflowTypeAndIdempotencyKey(
			UUID businessId,
			UUID requestedByUserId,
			AiWorkflowType workflowType,
			String idempotencyKey);
}
