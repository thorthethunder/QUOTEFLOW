package com.quoteflow.ai.action;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface AiActionProposalRepository extends JpaRepository<AiActionProposal, UUID> {

	@Query("""
			select p from AiActionProposal p
			where p.id = :id and p.businessId = :businessId
			""")
	Optional<AiActionProposal> findByIdAndBusinessId(
			@Param("id") UUID id, @Param("businessId") UUID businessId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select p from AiActionProposal p
			where p.id = :id and p.businessId = :businessId
			""")
	Optional<AiActionProposal> findByIdAndBusinessIdForUpdate(
			@Param("id") UUID id, @Param("businessId") UUID businessId);
}
