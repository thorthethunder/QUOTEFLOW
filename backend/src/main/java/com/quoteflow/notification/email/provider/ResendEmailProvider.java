package com.quoteflow.notification.email.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.notification.email.EmailAttachment;
import com.quoteflow.notification.email.EmailDeliveryResult;
import com.quoteflow.notification.email.EmailMessage;
import com.quoteflow.notification.email.EmailProperties;
import com.quoteflow.notification.email.EmailProvider;
import com.quoteflow.notification.email.EmailProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional Resend transactional email adapter. Verified against Resend emails API.
 * No hidden provider-level retries — notification outbox owns retry policy.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.email", name = "provider", havingValue = "RESEND")
public class ResendEmailProvider implements EmailProvider {

	private static final Logger log = LoggerFactory.getLogger(ResendEmailProvider.class);

	private final EmailProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public ResendEmailProvider(EmailProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.getResend().getConnectTimeout())
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.getResend().getReadTimeout());
		this.restClient = RestClient.builder()
				.baseUrl(trimSlash(properties.getResend().getApiBaseUrl()))
				.requestFactory(factory)
				.build();
	}

	@Override
	public String providerName() {
		return "RESEND";
	}

	@Override
	public EmailDeliveryResult send(EmailMessage message) {
		if (!StringUtils.hasText(properties.getResend().getApiKey())) {
			return EmailDeliveryResult.failure("EMAIL_CONFIGURATION_ERROR", false);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", formatFrom());
		body.put("to", List.of(message.toEmail()));
		body.put("subject", message.subject());
		body.put("html", message.htmlBody());
		body.put("text", message.textBody());
		if (StringUtils.hasText(message.replyToEmail())) {
			body.put("reply_to", message.replyToEmail());
		}
		if (StringUtils.hasText(message.idempotencyKey())) {
			body.put("headers", Map.of("Idempotency-Key", message.idempotencyKey()));
		}
		if (message.attachments() != null && !message.attachments().isEmpty()) {
			List<Map<String, String>> attachments = new ArrayList<>();
			for (EmailAttachment attachment : message.attachments()) {
				attachments.add(Map.of(
						"filename", attachment.filename(),
						"content", Base64.getEncoder().encodeToString(attachment.content())));
			}
			body.put("attachments", attachments);
		}

		try {
			String response = restClient.post()
					.uri("/emails")
					.contentType(MediaType.APPLICATION_JSON)
					.header("Authorization", "Bearer " + properties.getResend().getApiKey())
					.header("Idempotency-Key",
							StringUtils.hasText(message.idempotencyKey())
									? message.idempotencyKey()
									: UUID_FALLBACK())
					.body(body)
					.retrieve()
					.body(String.class);
			String id = parseId(response);
			log.info("email.resend.accepted messageIdPresent={}", id != null);
			return EmailDeliveryResult.success(id != null ? id : "resend_unknown");
		} catch (RestClientResponseException ex) {
			int status = ex.getStatusCode().value();
			boolean retryable = status == 429 || status >= 500;
			log.warn("email.resend.failed status={} retryable={}", status, retryable);
			return EmailDeliveryResult.failure(retryable ? "PROVIDER_TEMPORARY" : "PROVIDER_REJECTED", retryable);
		} catch (Exception ex) {
			log.warn("email.resend.failed code=PROVIDER_UNAVAILABLE");
			throw new EmailProviderException("PROVIDER_UNAVAILABLE", "Email provider unavailable", true, ex);
		}
	}

	private String formatFrom() {
		String name = properties.getFromName();
		String email = properties.getFromEmail();
		if (StringUtils.hasText(name)) {
			return name + " <" + email + ">";
		}
		return email;
	}

	private String parseId(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		try {
			JsonNode node = objectMapper.readTree(response);
			return node.path("id").asText(null);
		} catch (Exception ex) {
			return null;
		}
	}

	private static String trimSlash(String url) {
		if (url == null) {
			return "";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private static String UUID_FALLBACK() {
		return java.util.UUID.randomUUID().toString();
	}
}
