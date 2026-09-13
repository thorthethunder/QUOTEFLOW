package com.quoteflow.quotation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequence.DocumentSequenceId> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM DocumentSequence s WHERE s.businessId = :businessId AND s.documentType = :documentType")
	Optional<DocumentSequence> findForUpdate(
			@Param("businessId") UUID businessId,
			@Param("documentType") DocumentType documentType);
}
