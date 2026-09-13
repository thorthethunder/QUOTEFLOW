package com.quoteflow.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findByIdAndBusiness_Id(UUID id, UUID businessId);

	@Query("""
			select p from Payment p
			join fetch p.invoice i
			where p.id = :id and p.business.id = :businessId
			""")
	Optional<Payment> findDetailByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

	@Query("""
			select p from Payment p
			where p.invoice.id = :invoiceId and p.business.id = :businessId
			order by p.paymentDate desc, p.createdAt desc
			""")
	List<Payment> findByInvoiceOrdered(
			@Param("invoiceId") UUID invoiceId,
			@Param("businessId") UUID businessId);

	@Query("""
			select coalesce(sum(p.amount), 0) from Payment p
			where p.invoice.id = :invoiceId
			  and p.business.id = :businessId
			  and p.status = com.quoteflow.payment.PaymentRecordStatus.RECORDED
			""")
	BigDecimal sumRecordedAmount(
			@Param("invoiceId") UUID invoiceId,
			@Param("businessId") UUID businessId);

	@Query("""
			select count(p) from Payment p
			where p.invoice.id = :invoiceId
			  and p.business.id = :businessId
			  and p.status = com.quoteflow.payment.PaymentRecordStatus.RECORDED
			""")
	long countRecordedByInvoice(
			@Param("invoiceId") UUID invoiceId,
			@Param("businessId") UUID businessId);

	@Query("""
			select p.invoice.id, coalesce(sum(p.amount), 0)
			from Payment p
			where p.business.id = :businessId
			  and p.status = com.quoteflow.payment.PaymentRecordStatus.RECORDED
			  and p.invoice.id in :invoiceIds
			group by p.invoice.id
			""")
	List<Object[]> sumRecordedByInvoiceIds(
			@Param("businessId") UUID businessId,
			@Param("invoiceIds") Collection<UUID> invoiceIds);
}
