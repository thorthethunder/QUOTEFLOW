package com.quoteflow.quotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

	Optional<Quotation> findByIdAndBusiness_Id(UUID id, UUID businessId);

	@EntityGraph(attributePaths = "items")
	@Query("SELECT q FROM Quotation q WHERE q.id = :id AND q.business.id = :businessId")
	Optional<Quotation> findDetailByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "items")
	@Query("SELECT q FROM Quotation q WHERE q.id = :id AND q.business.id = :businessId")
	Optional<Quotation> findDetailByIdAndBusinessIdForUpdate(
			@Param("id") UUID id,
			@Param("businessId") UUID businessId);

	@Query("""
			SELECT q FROM Quotation q
			WHERE q.business.id = :businessId
			  AND (:status IS NULL OR q.status = :status)
			  AND (
			    :q IS NULL OR :q = '' OR
			    LOWER(q.quotationNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR
			    LOWER(q.customerDisplayName) LIKE LOWER(CONCAT('%', :q, '%')) OR
			    LOWER(COALESCE(q.customerCompanyName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
			  )
			""")
	Page<Quotation> search(
			@Param("businessId") UUID businessId,
			@Param("status") QuotationStatus status,
			@Param("q") String q,
			Pageable pageable);

	@Query("""
			SELECT COUNT(q) FROM Quotation q
			WHERE q.business.id = :businessId
			  AND q.createdAt >= :startInclusive
			  AND q.createdAt < :endExclusive
			""")
	long countCreatedInPeriod(
			@Param("businessId") UUID businessId,
			@Param("startInclusive") Instant startInclusive,
			@Param("endExclusive") Instant endExclusive);
}
