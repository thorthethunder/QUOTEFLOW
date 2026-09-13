package com.quoteflow.billing.provider.fake;

import com.quoteflow.billing.provider.BillingProvider;
import com.quoteflow.billing.provider.BillingProviderException;
import com.quoteflow.billing.provider.CreateProviderSubscriptionCommand;
import com.quoteflow.billing.provider.ProviderSubscription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory billing provider for integration tests. No network I/O.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.billing", name = "provider", havingValue = "FAKE")
public class FakeBillingProvider implements BillingProvider {

	private final ConcurrentHashMap<String, ProviderSubscription> store = new ConcurrentHashMap<>();
	private final AtomicInteger createCalls = new AtomicInteger();

	@Override
	public String providerName() {
		return "FAKE";
	}

	@Override
	public ProviderSubscription createSubscription(CreateProviderSubscriptionCommand command) {
		createCalls.incrementAndGet();
		String id = "sub_fake_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
		Instant now = Instant.now();
		ProviderSubscription created = new ProviderSubscription(
				id,
				null,
				command.providerPlanId(),
				"created",
				null,
				null,
				false,
				null,
				now,
				command.notes() == null ? Map.of() : Map.copyOf(command.notes()));
		store.put(id, created);
		return created;
	}

	@Override
	public ProviderSubscription fetchSubscription(String providerSubscriptionId) {
		ProviderSubscription sub = store.get(providerSubscriptionId);
		if (sub == null) {
			throw new BillingProviderException("PROVIDER_NOT_FOUND", "Fake subscription not found");
		}
		return sub;
	}

	@Override
	public ProviderSubscription cancelSubscription(String providerSubscriptionId, boolean cancelAtCycleEnd) {
		ProviderSubscription existing = fetchSubscription(providerSubscriptionId);
		Instant now = Instant.now();
		ProviderSubscription updated = new ProviderSubscription(
				existing.providerSubscriptionId(),
				existing.providerCustomerId(),
				existing.providerPlanId(),
				cancelAtCycleEnd ? existing.status() : "cancelled",
				existing.currentPeriodStart(),
				existing.currentPeriodEnd(),
				cancelAtCycleEnd,
				cancelAtCycleEnd ? null : now,
				now,
				existing.notes());
		store.put(providerSubscriptionId, updated);
		return updated;
	}

	public ProviderSubscription simulateActivated(String providerSubscriptionId) {
		ProviderSubscription existing = fetchSubscription(providerSubscriptionId);
		Instant start = Instant.now();
		Instant end = start.plus(30, ChronoUnit.DAYS);
		ProviderSubscription updated = new ProviderSubscription(
				existing.providerSubscriptionId(),
				existing.providerCustomerId(),
				existing.providerPlanId(),
				"active",
				start,
				end,
				existing.cancelAtCycleEnd(),
				null,
				Instant.now(),
				existing.notes());
		store.put(providerSubscriptionId, updated);
		return updated;
	}

	public ProviderSubscription put(ProviderSubscription subscription) {
		store.put(subscription.providerSubscriptionId(), subscription);
		return subscription;
	}

	public int createCallCount() {
		return createCalls.get();
	}

	public void reset() {
		store.clear();
		createCalls.set(0);
	}
}
