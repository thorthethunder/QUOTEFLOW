package com.quoteflow.subscription;

import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import com.quoteflow.subscription.dto.EntitlementResponse;
import com.quoteflow.subscription.dto.PlanCatalogItemDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Subscription")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

	private final SubscriptionService subscriptionService;

	public SubscriptionController(SubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	@GetMapping("/subscription")
	@Operation(summary = "Current tenant plan, usage, and feature entitlements")
	public EntitlementResponse current() {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return subscriptionService.getEntitlements(principal.getBusinessId());
	}

	@GetMapping("/plans")
	@Operation(summary = "Public-ish authenticated plan catalog (pricing informational until Phase 12)")
	public List<PlanCatalogItemDto> plans() {
		SecurityUtils.requireCurrentUser();
		return subscriptionService.catalog();
	}
}
