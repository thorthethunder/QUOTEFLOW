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
	 * spring-ai (default) or legacy-rest (Phase 1 RestClient adapter).
	 */
	private String adapter = "spring-ai";

	/**
	 * When true, logs truncated prompts at DEBUG — never enable by default in production.
	 */
	private boolean logPrompts = false;

	private int maxResponseChars = 256_000;

	private final Ollama ollama = new Ollama();
	private final QuoteAssistant quoteAssistant = new QuoteAssistant();

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

	public String getAdapter() {
		return adapter;
	}

	public void setAdapter(String adapter) {
		this.adapter = adapter;
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

	public QuoteAssistant getQuoteAssistant() {
		return quoteAssistant;
	}

	public static class QuoteAssistant {
		private int maxPromptChars = 4000;
		private int perUserPerMinute = 10;
		private int perTenantPerMinute = 30;

		public int getMaxPromptChars() {
			return maxPromptChars;
		}

		public void setMaxPromptChars(int maxPromptChars) {
			this.maxPromptChars = Math.max(200, Math.min(maxPromptChars, 20_000));
		}

		public int getPerUserPerMinute() {
			return perUserPerMinute;
		}

		public void setPerUserPerMinute(int perUserPerMinute) {
			this.perUserPerMinute = Math.max(1, perUserPerMinute);
		}

		public int getPerTenantPerMinute() {
			return perTenantPerMinute;
		}

		public void setPerTenantPerMinute(int perTenantPerMinute) {
			this.perTenantPerMinute = Math.max(1, perTenantPerMinute);
		}
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
