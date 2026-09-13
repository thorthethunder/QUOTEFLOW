package com.quoteflow.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	Optional<Customer> findByIdAndBusiness_Id(UUID id, UUID businessId);

	long countByBusiness_IdAndStatus(UUID businessId, CustomerStatus status);

	@Query("""
			SELECT c FROM Customer c
			WHERE c.business.id = :businessId
			  AND (:status IS NULL OR c.status = :status)
			  AND (
			    :q IS NULL OR :q = '' OR
			    LOWER(c.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR
			    LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
			    LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
			    LOWER(COALESCE(c.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
			  )
			""")
	Page<Customer> search(
			@Param("businessId") UUID businessId,
			@Param("status") CustomerStatus status,
			@Param("q") String q,
			Pageable pageable);
}
