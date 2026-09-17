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
	private final BusinessCopilot businessCopilot = new BusinessCopilot();
	private final Actions actions = new Actions();

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

	public BusinessCopilot getBusinessCopilot() {
		return businessCopilot;
	}

	public Actions getActions() {
		return actions;
	}

	public static class Actions {
		public static final Duration HARD_MAX_APPROVAL_TTL = Duration.ofHours(1);
		public static final int HARD_MAX_ITEMS = 50;
		public static final int HARD_MAX_PAYLOAD_CHARS = 64_000;

		/** Master switch for AI-assisted actions. Default false. */
		private boolean enabled = false;
		private Duration approvalTtl = Duration.ofMinutes(10);
		private int perUserPerMinute = 6;
		private int perTenantPerMinute = 20;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Duration getApprovalTtl() {
			return approvalTtl;
		}

		public void setApprovalTtl(Duration approvalTtl) {
			if (approvalTtl == null || approvalTtl.isNegative() || approvalTtl.isZero()) {
				this.approvalTtl = Duration.ofMinutes(10);
				return;
			}
			if (approvalTtl.compareTo(HARD_MAX_APPROVAL_TTL) > 0) {
				this.approvalTtl = HARD_MAX_APPROVAL_TTL;
			} else if (approvalTtl.compareTo(Duration.ofMinutes(1)) < 0) {
				this.approvalTtl = Duration.ofMinutes(1);
			} else {
				this.approvalTtl = approvalTtl;
			}
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

	public static class BusinessCopilot {
		/** Hard upper bound — client cannot raise this. */
		public static final int HARD_MAX_TOOL_CALLS = 8;
		public static final int HARD_MAX_MESSAGE_CHARS = 2000;
		public static final int HARD_MAX_RESULT_ROWS = 20;

		private int maxMessageChars = 2000;
		private int maxToolCalls = 6;
		private int perUserPerMinute = 6;
		private int perTenantPerMinute = 20;
		private boolean exposeModelInCapabilities = false;

		public int getMaxMessageChars() {
			return maxMessageChars;
		}

		public void setMaxMessageChars(int maxMessageChars) {
			this.maxMessageChars = Math.max(200, Math.min(maxMessageChars, HARD_MAX_MESSAGE_CHARS));
		}

		public int getMaxToolCalls() {
			return maxToolCalls;
		}

		public void setMaxToolCalls(int maxToolCalls) {
			this.maxToolCalls = Math.max(1, Math.min(maxToolCalls, HARD_MAX_TOOL_CALLS));
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

		public boolean isExposeModelInCapabilities() {
			return exposeModelInCapabilities;
		}

		public void setExposeModelInCapabilities(boolean exposeModelInCapabilities) {
			this.exposeModelInCapabilities = exposeModelInCapabilities;
		}
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
