package com.quoteflow.billing.checkout;

import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing")
@SecurityRequirement(name = "bearerAuth")
public class BillingCheckoutController {

	private final BillingCheckoutService billingCheckoutService;

	public BillingCheckoutController(BillingCheckoutService billingCheckoutService) {
		this.billingCheckoutService = billingCheckoutService;
	}

	@PostMapping("/checkout")
	@Operation(summary = "Create Razorpay subscription checkout session (OWNER only)")
	public CheckoutResponse createCheckout(@Valid @RequestBody CreateCheckoutRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return billingCheckoutService.createCheckout(principal, request);
	}

	@PostMapping("/checkout/verify")
	@Operation(summary = "Verify Razorpay checkout signature and refresh subscription (OWNER only)")
	public VerifyCheckoutResponse verifyCheckout(@Valid @RequestBody VerifyCheckoutRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return billingCheckoutService.verifyCheckout(principal, request);
	}

	@PostMapping("/subscription/cancel")
	@Operation(summary = "Cancel paid subscription at period end (OWNER only)")
	public VerifyCheckoutResponse cancel() {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return billingCheckoutService.cancelAtPeriodEnd(principal);
	}
}
