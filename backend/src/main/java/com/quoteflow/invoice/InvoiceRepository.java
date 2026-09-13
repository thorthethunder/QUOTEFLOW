package com.quoteflow.invoice;

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

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

	@EntityGraph(attributePaths = {"items", "sourceQuotation"})
	@Query("select i from Invoice i where i.id = :id and i.business.id = :businessId")
	Optional<Invoice> findDetailByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Invoice i where i.id = :id and i.business.id = :businessId")
	Optional<Invoice> findByIdAndBusinessIdForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);

	Optional<Invoice> findBySourceQuotation_IdAndBusiness_Id(UUID sourceQuotationId, UUID businessId);

	Optional<Invoice> findBySourceQuotation_Id(UUID sourceQuotationId);

	@Query("""
			select i from Invoice i
			where i.business.id = :businessId
			  and (:status is null or i.status = :status)
			  and (
			    :q is null
			    or lower(i.invoiceNumber) like lower(concat('%', cast(:q as string), '%'))
			    or lower(i.customerDisplayName) like lower(concat('%', cast(:q as string), '%'))
			    or (i.customerCompanyName is not null and lower(i.customerCompanyName) like lower(concat('%', cast(:q as string), '%')))
			  )
			""")
	Page<Invoice> search(
			@Param("businessId") UUID businessId,
			@Param("status") InvoiceStatus status,
			@Param("q") String q,
			Pageable pageable);

	@Query("""
			SELECT COUNT(i) FROM Invoice i
			WHERE i.business.id = :businessId
			  AND i.createdAt >= :startInclusive
			  AND i.createdAt < :endExclusive
			""")
	long countCreatedInPeriod(
			@Param("businessId") UUID businessId,
			@Param("startInclusive") Instant startInclusive,
			@Param("endExclusive") Instant endExclusive);
}
