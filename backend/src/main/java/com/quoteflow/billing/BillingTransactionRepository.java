package com.quoteflow.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, UUID> {

	Optional<BillingTransaction> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);
}
