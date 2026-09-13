package com.quoteflow.billing.webhook;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Public Razorpay webhook endpoint. Authenticated by webhook HMAC, not JWT.
 * Raw body is read before JSON parse for signature verification.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Hidden
public class RazorpayWebhookController {

	private final BillingWebhookService billingWebhookService;

	public RazorpayWebhookController(BillingWebhookService billingWebhookService) {
		this.billingWebhookService = billingWebhookService;
	}

	@PostMapping("/razorpay")
	public ResponseEntity<Void> receive(
			HttpServletRequest request,
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
			@RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId)
			throws IOException {
		byte[] bytes = request.getInputStream().readAllBytes();
		String rawBody = new String(bytes, StandardCharsets.UTF_8);
		billingWebhookService.handleRazorpayWebhook(rawBody, signature, eventId);
		return ResponseEntity.ok().build();
	}
}
