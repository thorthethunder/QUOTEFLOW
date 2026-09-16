package com.quoteflow.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "quoteflow.ai")
public class AiProperties {

	/**
	 * Master switch. Default false — core SaaS must not require AI.
	 */
	private boolean enabled = false;

	/**
	 * Provider type when enabled: OLLAMA (Phase 1). Others fail closed until implemented.
	 */
	private String provider = "OLLAMA";

	/**
	 * When true, logs truncated prompts at DEBUG — never enable by default in production.
	 */
	private boolean logPrompts = false;

	private int maxResponseChars = 256_000;

	private final Ollama ollama = new Ollama();

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

	public boolean isLogPrompts() {
		return logPrompts;
	}

	public void setLogPrompts(boolean logPrompts) {
		this.logPrompts = logPrompts;
	}

	public int getMaxResponseChars() {
		return maxResponseChars;
	}

	public void setMaxResponseChars(int maxResponseChars) {
		if (maxResponseChars < 1_024) {
			this.maxResponseChars = 1_024;
		} else if (maxResponseChars > 2_000_000) {
			this.maxResponseChars = 2_000_000;
		} else {
			this.maxResponseChars = maxResponseChars;
		}
	}

	public Ollama getOllama() {
		return ollama;
	}

	public static class Ollama {
		/** Trusted server-side origin only. Never from browser request. */
		private String baseUrl = "http://localhost:11434";
		/** Configured model id — do not hardcode in business logic. Recommended local: qwen3:8b */
		private String model = "qwen3:8b";
		private Duration connectTimeout = Duration.ofSeconds(5);
		private Duration readTimeout = Duration.ofSeconds(120);

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
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
