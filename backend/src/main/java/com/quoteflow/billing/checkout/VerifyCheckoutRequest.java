package com.quoteflow.billing.checkout;

import jakarta.validation.constraints.NotBlank;

public record VerifyCheckoutRequest(
		@NotBlank String razorpayPaymentId,
		@NotBlank String razorpaySubscriptionId,
		@NotBlank String razorpaySignature
) {
}
