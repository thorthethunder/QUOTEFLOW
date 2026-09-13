package com.quoteflow.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

	Optional<Subscription> findByBusinessId(UUID businessId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Subscription s WHERE s.businessId = :businessId")
	Optional<Subscription> findByBusinessIdForUpdate(@Param("businessId") UUID businessId);

	Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId);

	boolean existsByBusinessId(UUID businessId);
}
