package com.quoteflow.billing.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEvent, UUID> {

	Optional<BillingWebhookEvent> findByProviderAndProviderEventId(String provider, String providerEventId);

	boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
