package com.quoteflow.billing.provider.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.billing.BillingProperties;
import com.quoteflow.billing.provider.BillingProvider;
import com.quoteflow.billing.provider.BillingProviderException;
import com.quoteflow.billing.provider.CreateProviderSubscriptionCommand;
import com.quoteflow.billing.provider.ProviderSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Razorpay Subscriptions adapter. Uses bounded HTTP timeouts. No write retries.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.billing", name = "provider", havingValue = "RAZORPAY", matchIfMissing = true)
public class RazorpayBillingProvider implements BillingProvider {

	private static final Logger log = LoggerFactory.getLogger(RazorpayBillingProvider.class);

	private final BillingProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public RazorpayBillingProvider(BillingProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.getRazorpay().getConnectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getRazorpay().getReadTimeout());
		this.restClient = RestClient.builder()
				.baseUrl(trimTrailingSlash(properties.getRazorpay().getApiBaseUrl()))
				.requestFactory(requestFactory)
				.defaultHeader("Authorization", basicAuth(
						properties.getRazorpay().getKeyId(),
						properties.getRazorpay().getKeySecret()))
				.build();
	}

	@Override
	public String providerName() {
		return "RAZORPAY";
	}

	@Override
	public ProviderSubscription createSubscription(CreateProviderSubscriptionCommand command) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("plan_id", command.providerPlanId());
		body.put("total_count", command.totalCount());
		body.put("quantity", 1);
		body.put("customer_notify", 1);
		if (command.notes() != null && !command.notes().isEmpty()) {
			body.put("notes", command.notes());
		}
		return exchange("POST", "/subscriptions", body, false);
	}

	@Override
	public ProviderSubscription fetchSubscription(String providerSubscriptionId) {
		try {
			return exchange("GET", "/subscriptions/" + providerSubscriptionId, null, true);
		} catch (BillingProviderException ex) {
			if (ex.isRetryableRead()) {
				log.warn("billing.provider.fetch.retry code={}", ex.getCode());
				return exchange("GET", "/subscriptions/" + providerSubscriptionId, null, false);
			}
			throw ex;
		}
	}

	@Override
	public ProviderSubscription cancelSubscription(String providerSubscriptionId, boolean cancelAtCycleEnd) {
		Map<String, Object> body = Map.of("cancel_at_cycle_end", cancelAtCycleEnd);
		return exchange("POST", "/subscriptions/" + providerSubscriptionId + "/cancel", body, false);
	}

	private ProviderSubscription exchange(String method, String path, Map<String, Object> body, boolean markRetryable) {
		try {
			String responseBody;
			if ("GET".equals(method)) {
				responseBody = restClient.get()
						.uri(path)
						.retrieve()
						.body(String.class);
			} else {
				responseBody = restClient.post()
						.uri(path)
						.contentType(MediaType.APPLICATION_JSON)
						.body(body == null ? Map.of() : body)
						.retrieve()
						.body(String.class);
			}
			return mapSubscription(responseBody);
		} catch (RestClientResponseException ex) {
			log.warn("billing.provider.http_error status={} path={}", ex.getStatusCode().value(), path);
			throw new BillingProviderException(
					"PROVIDER_HTTP_ERROR",
					"Billing provider request failed",
					markRetryable && ex.getStatusCode().is5xxServerError(),
					ex);
		} catch (Exception ex) {
			log.warn("billing.provider.unavailable path={}", path);
			throw new BillingProviderException(
					"PROVIDER_UNAVAILABLE",
					"Billing provider unavailable",
					markRetryable,
					ex);
		}
	}

	ProviderSubscription mapSubscription(String json) {
		try {
			JsonNode root = objectMapper.readTree(json);
			Map<String, String> notes = new HashMap<>();
			JsonNode notesNode = root.path("notes");
			if (notesNode.isObject()) {
				Iterator<String> names = notesNode.fieldNames();
				while (names.hasNext()) {
					String key = names.next();
					notes.put(key, notesNode.path(key).asText(null));
				}
			}
			return new ProviderSubscription(
					text(root, "id"),
					text(root, "customer_id"),
					text(root, "plan_id"),
					text(root, "status"),
					epoch(root, "current_start"),
					epoch(root, "current_end"),
					root.path("cancel_at_cycle_end").asBoolean(false)
							|| root.path("has_scheduled_changes").asBoolean(false),
					epoch(root, "ended_at"),
					epoch(root, "created_at"),
					Map.copyOf(notes));
		} catch (BillingProviderException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new BillingProviderException("PROVIDER_PARSE_ERROR", "Unable to parse provider subscription", false, ex);
		}
	}

	private static String text(JsonNode root, String field) {
		JsonNode node = root.get(field);
		if (node == null || node.isNull()) {
			return null;
		}
		String value = node.asText();
		return value == null || value.isBlank() ? null : value;
	}

	private static Instant epoch(JsonNode root, String field) {
		JsonNode node = root.get(field);
		if (node == null || node.isNull() || !node.canConvertToLong()) {
			return null;
		}
		long value = node.asLong();
		return value <= 0 ? null : Instant.ofEpochSecond(value);
	}

	private static String basicAuth(String keyId, String keySecret) {
		String raw = (keyId == null ? "" : keyId) + ":" + (keySecret == null ? "" : keySecret);
		return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static String trimTrailingSlash(String url) {
		if (url == null || url.isBlank()) {
			return "https://api.razorpay.com/v1";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
