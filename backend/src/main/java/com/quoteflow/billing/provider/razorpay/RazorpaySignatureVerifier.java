package com.quoteflow.billing.provider.razorpay;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC helpers for Razorpay checkout and webhook signatures.
 * Constant-time compare for all verifications.
 */
@Component
public class RazorpaySignatureVerifier {

	public String hmacSha256Hex(String payload, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to compute HMAC", ex);
		}
	}

	public boolean verifyCheckoutSignature(String paymentId, String subscriptionId, String signature, String keySecret) {
		if (isBlank(paymentId) || isBlank(subscriptionId) || isBlank(signature) || isBlank(keySecret)) {
			return false;
		}
		String expected = hmacSha256Hex(paymentId + "|" + subscriptionId, keySecret);
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				signature.getBytes(StandardCharsets.UTF_8));
	}

	public boolean verifyWebhookSignature(String rawBody, String signature, String webhookSecret) {
		if (rawBody == null || isBlank(signature) || isBlank(webhookSecret)) {
			return false;
		}
		String expected = hmacSha256Hex(rawBody, webhookSecret);
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				signature.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public String sha256Hex(String payload) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to hash payload", ex);
		}
	}
}
