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
import java.util.List;
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

	@Query(value = """
			SELECT
			  i.id AS id,
			  i.invoice_number AS invoiceNumber,
			  i.customer_display_name AS customerDisplayName,
			  i.customer_email AS customerEmail,
			  i.currency AS currency,
			  i.due_date AS dueDate,
			  i.total_amount AS totalAmount,
			  COALESCE(p.amount_paid, 0) AS amountPaid,
			  (i.total_amount - COALESCE(p.amount_paid, 0)) AS balanceDue
			FROM invoices i
			LEFT JOIN (
			  SELECT invoice_id, SUM(amount) AS amount_paid
			  FROM payments
			  WHERE business_id = :businessId
			    AND status = 'RECORDED'
			  GROUP BY invoice_id
			) p ON p.invoice_id = i.id
			WHERE i.business_id = :businessId
			  AND i.status = 'SENT'
			  AND (i.total_amount - COALESCE(p.amount_paid, 0)) > 0
			ORDER BY balanceDue DESC, i.due_date NULLS LAST, i.invoice_number ASC
			LIMIT :limit
			""", nativeQuery = true)
	List<OutstandingInvoiceCandidate> findTopOutstandingForPaymentFollowUp(
			@Param("businessId") UUID businessId,
			@Param("limit") int limit);
}
