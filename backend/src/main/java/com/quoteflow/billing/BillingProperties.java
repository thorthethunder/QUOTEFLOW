package com.quoteflow.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "quoteflow.billing")
public class BillingProperties {

	/**
	 * Master switch. When false, FREE product works and checkout returns BILLING_NOT_AVAILABLE.
	 */
	private boolean enabled = false;

	/**
	 * RAZORPAY for real adapter; FAKE for automated tests.
	 */
	private String provider = "RAZORPAY";

	private final Razorpay razorpay = new Razorpay();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public Razorpay getRazorpay() {
		return razorpay;
	}

	public static class Razorpay {
		private String keyId = "";
		private String keySecret = "";
		private String webhookSecret = "";
		private String apiBaseUrl = "https://api.razorpay.com/v1";
		private Duration connectTimeout = Duration.ofSeconds(5);
		private Duration readTimeout = Duration.ofSeconds(15);
		private String proMonthlyPlanId = "";
		private String proAnnualPlanId = "";
		private String businessMonthlyPlanId = "";
		private int monthlyTotalCount = 120;
		private int annualTotalCount = 10;

		public String getKeyId() {
			return keyId;
		}

		public void setKeyId(String keyId) {
			this.keyId = keyId;
		}

		public String getKeySecret() {
			return keySecret;
		}

		public void setKeySecret(String keySecret) {
			this.keySecret = keySecret;
		}

		public String getWebhookSecret() {
			return webhookSecret;
		}

		public void setWebhookSecret(String webhookSecret) {
			this.webhookSecret = webhookSecret;
		}

		public String getApiBaseUrl() {
			return apiBaseUrl;
		}

		public void setApiBaseUrl(String apiBaseUrl) {
			this.apiBaseUrl = apiBaseUrl;
		}

		public Duration getConnectTimeout() {
			return connectTimeout;
		}

		public void setConnectTimeout(Duration connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		public Duration getReadTimeout() {
			return readTimeout;
		}

		public void setReadTimeout(Duration readTimeout) {
			this.readTimeout = readTimeout;
		}

		public String getProMonthlyPlanId() {
			return proMonthlyPlanId;
		}

		public void setProMonthlyPlanId(String proMonthlyPlanId) {
			this.proMonthlyPlanId = proMonthlyPlanId;
		}

		public String getProAnnualPlanId() {
			return proAnnualPlanId;
		}

		public void setProAnnualPlanId(String proAnnualPlanId) {
			this.proAnnualPlanId = proAnnualPlanId;
		}

		public String getBusinessMonthlyPlanId() {
			return businessMonthlyPlanId;
		}

		public void setBusinessMonthlyPlanId(String businessMonthlyPlanId) {
			this.businessMonthlyPlanId = businessMonthlyPlanId;
		}

		public int getMonthlyTotalCount() {
			return monthlyTotalCount;
		}

		public void setMonthlyTotalCount(int monthlyTotalCount) {
			this.monthlyTotalCount = monthlyTotalCount;
		}

		public int getAnnualTotalCount() {
			return annualTotalCount;
		}

		public void setAnnualTotalCount(int annualTotalCount) {
			this.annualTotalCount = annualTotalCount;
		}
	}
}
