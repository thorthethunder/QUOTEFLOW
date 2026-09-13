package com.quoteflow.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "quoteflow.email")
public class EmailProperties {

	/**
	 * CONSOLE (default local), FAKE (tests), RESEND (optional production provider).
	 */
	private String provider = "CONSOLE";

	private String fromEmail = "noreply@quoteflow.local";
	private String fromName = "QuoteFlow";
	private int maxAttempts = 4;
	private int workerBatchSize = 20;
	private Duration workerInterval = Duration.ofSeconds(5);
	/**
	 * Stale SENDING rows older than this lease are reclaimable after worker crash.
	 * Must exceed typical PDF+provider send duration.
	 */
	private Duration claimLease = Duration.ofMinutes(10);
	private boolean workerEnabled = true;
	private int maxPdfAttachmentBytes = 2_000_000;
	private int maxCustomMessageLength = 500;
	private int perTenantPerMinute = 30;
	private int perDocumentPerMinute = 5;

	private final Resend resend = new Resend();

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getFromEmail() {
		return fromEmail;
	}

	public void setFromEmail(String fromEmail) {
		this.fromEmail = fromEmail;
	}

	public String getFromName() {
		return fromName;
	}

	public void setFromName(String fromName) {
		this.fromName = fromName;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public int getWorkerBatchSize() {
		return workerBatchSize;
	}

	public void setWorkerBatchSize(int workerBatchSize) {
		if (workerBatchSize < 1) {
			this.workerBatchSize = 1;
		} else if (workerBatchSize > 100) {
			this.workerBatchSize = 100;
		} else {
			this.workerBatchSize = workerBatchSize;
		}
	}

	public Duration getWorkerInterval() {
		return workerInterval;
	}

	public void setWorkerInterval(Duration workerInterval) {
		this.workerInterval = workerInterval;
	}

	public Duration getClaimLease() {
		return claimLease;
	}

	public void setClaimLease(Duration claimLease) {
		if (claimLease == null || claimLease.isNegative() || claimLease.isZero()) {
			this.claimLease = Duration.ofMinutes(10);
		} else if (claimLease.compareTo(Duration.ofHours(2)) > 0) {
			this.claimLease = Duration.ofHours(2);
		} else {
			this.claimLease = claimLease;
		}
	}

	public boolean isWorkerEnabled() {
		return workerEnabled;
	}

	public void setWorkerEnabled(boolean workerEnabled) {
		this.workerEnabled = workerEnabled;
	}

	public int getMaxPdfAttachmentBytes() {
		return maxPdfAttachmentBytes;
	}

	public void setMaxPdfAttachmentBytes(int maxPdfAttachmentBytes) {
		this.maxPdfAttachmentBytes = maxPdfAttachmentBytes;
	}

	public int getMaxCustomMessageLength() {
		return maxCustomMessageLength;
	}

	public void setMaxCustomMessageLength(int maxCustomMessageLength) {
		this.maxCustomMessageLength = maxCustomMessageLength;
	}

	public int getPerTenantPerMinute() {
		return perTenantPerMinute;
	}

	public void setPerTenantPerMinute(int perTenantPerMinute) {
		this.perTenantPerMinute = perTenantPerMinute;
	}

	public int getPerDocumentPerMinute() {
		return perDocumentPerMinute;
	}

	public void setPerDocumentPerMinute(int perDocumentPerMinute) {
		this.perDocumentPerMinute = perDocumentPerMinute;
	}

	public Resend getResend() {
		return resend;
	}

	public static class Resend {
		private String apiKey = "";
		private String apiBaseUrl = "https://api.resend.com";
		private Duration connectTimeout = Duration.ofSeconds(5);
		private Duration readTimeout = Duration.ofSeconds(15);

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
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
	}
}
