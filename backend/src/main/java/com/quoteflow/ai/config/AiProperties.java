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
	private final ReportingInsights reportingInsights = new ReportingInsights();
	private final Knowledge knowledge = new Knowledge();
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

	public ReportingInsights getReportingInsights() {
		return reportingInsights;
	}

	public Knowledge getKnowledge() {
		return knowledge;
	}

	public Actions getActions() {
		return actions;
	}

	public static class Knowledge {
		public static final int HARD_MAX_TITLE_CHARS = 140;
		public static final int HARD_MAX_FILENAME_CHARS = 180;
		public static final int HARD_MAX_FILE_BYTES = 1_000_000;
		public static final int HARD_MAX_TEXT_CHARS = 120_000;
		public static final int HARD_MAX_CHUNKS_PER_DOCUMENT = 80;
		public static final int HARD_MAX_CHUNK_CHARS = 1_500;
		public static final int HARD_MAX_OVERLAP_CHARS = 250;
		public static final int HARD_MAX_TOP_K = 8;
		public static final int HARD_MAX_SOURCE_EXCERPT_CHARS = 360;
		public static final int HARD_MAX_QUESTION_CHARS = 1000;

		private boolean enabled = false;
		private String embeddingProvider = "OLLAMA";
		private String embeddingModel = "mxbai-embed-large";
		private int embeddingDimension = 1024;
		private int maxTitleChars = 140;
		private int maxFilenameChars = 180;
		private int maxFileBytes = 500_000;
		private int maxTextChars = 80_000;
		private int maxChunksPerDocument = 60;
		private int chunkSize = 900;
		private int chunkOverlap = 120;
		private int topK = 5;
		private double relevanceThreshold = 0.55d;
		private int sourceExcerptChars = 280;
		private int maxQuestionChars = 1000;
		private int queryPerUserPerMinute = 10;
		private int queryPerTenantPerMinute = 30;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getEmbeddingProvider() {
			return embeddingProvider;
		}

		public void setEmbeddingProvider(String embeddingProvider) {
			this.embeddingProvider = embeddingProvider == null ? "OLLAMA" : embeddingProvider.trim().toUpperCase();
		}

		public String getEmbeddingModel() {
			return embeddingModel;
		}

		public void setEmbeddingModel(String embeddingModel) {
			this.embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
		}

		public int getEmbeddingDimension() {
			return embeddingDimension;
		}

		public void setEmbeddingDimension(int embeddingDimension) {
			this.embeddingDimension = Math.max(8, Math.min(embeddingDimension, 8192));
		}

		public int getMaxTitleChars() {
			return maxTitleChars;
		}

		public void setMaxTitleChars(int maxTitleChars) {
			this.maxTitleChars = Math.max(10, Math.min(maxTitleChars, HARD_MAX_TITLE_CHARS));
		}

		public int getMaxFilenameChars() {
			return maxFilenameChars;
		}

		public void setMaxFilenameChars(int maxFilenameChars) {
			this.maxFilenameChars = Math.max(20, Math.min(maxFilenameChars, HARD_MAX_FILENAME_CHARS));
		}

		public int getMaxFileBytes() {
			return maxFileBytes;
		}

		public void setMaxFileBytes(int maxFileBytes) {
			this.maxFileBytes = Math.max(1024, Math.min(maxFileBytes, HARD_MAX_FILE_BYTES));
		}

		public int getMaxTextChars() {
			return maxTextChars;
		}

		public void setMaxTextChars(int maxTextChars) {
			this.maxTextChars = Math.max(500, Math.min(maxTextChars, HARD_MAX_TEXT_CHARS));
		}

		public int getMaxChunksPerDocument() {
			return maxChunksPerDocument;
		}

		public void setMaxChunksPerDocument(int maxChunksPerDocument) {
			this.maxChunksPerDocument = Math.max(1, Math.min(maxChunksPerDocument, HARD_MAX_CHUNKS_PER_DOCUMENT));
		}

		public int getChunkSize() {
			return chunkSize;
		}

		public void setChunkSize(int chunkSize) {
			this.chunkSize = Math.max(200, Math.min(chunkSize, HARD_MAX_CHUNK_CHARS));
		}

		public int getChunkOverlap() {
			return chunkOverlap;
		}

		public void setChunkOverlap(int chunkOverlap) {
			this.chunkOverlap = Math.max(0, Math.min(chunkOverlap, HARD_MAX_OVERLAP_CHARS));
		}

		public int getTopK() {
			return topK;
		}

		public void setTopK(int topK) {
			this.topK = Math.max(1, Math.min(topK, HARD_MAX_TOP_K));
		}

		public double getRelevanceThreshold() {
			return relevanceThreshold;
		}

		public void setRelevanceThreshold(double relevanceThreshold) {
			this.relevanceThreshold = Math.max(0.0d, Math.min(relevanceThreshold, 0.99d));
		}

		public int getSourceExcerptChars() {
			return sourceExcerptChars;
		}

		public void setSourceExcerptChars(int sourceExcerptChars) {
			this.sourceExcerptChars = Math.max(80, Math.min(sourceExcerptChars, HARD_MAX_SOURCE_EXCERPT_CHARS));
		}

		public int getMaxQuestionChars() {
			return maxQuestionChars;
		}

		public void setMaxQuestionChars(int maxQuestionChars) {
			this.maxQuestionChars = Math.max(50, Math.min(maxQuestionChars, HARD_MAX_QUESTION_CHARS));
		}

		public int getQueryPerUserPerMinute() {
			return queryPerUserPerMinute;
		}

		public void setQueryPerUserPerMinute(int queryPerUserPerMinute) {
			this.queryPerUserPerMinute = Math.max(1, queryPerUserPerMinute);
		}

		public int getQueryPerTenantPerMinute() {
			return queryPerTenantPerMinute;
		}

		public void setQueryPerTenantPerMinute(int queryPerTenantPerMinute) {
			this.queryPerTenantPerMinute = Math.max(1, queryPerTenantPerMinute);
		}
	}

	public static class ReportingInsights {
		public static final int HARD_MAX_MESSAGE_CHARS = 1000;
		public static final int HARD_MAX_RESULT_ROWS = 10;

		private int maxMessageChars = 1000;
		private int maxOutstandingRows = 5;
		private int perUserPerMinute = 6;
		private int perTenantPerMinute = 20;

		public int getMaxMessageChars() {
			return maxMessageChars;
		}

		public void setMaxMessageChars(int maxMessageChars) {
			this.maxMessageChars = Math.max(100, Math.min(maxMessageChars, HARD_MAX_MESSAGE_CHARS));
		}

		public int getMaxOutstandingRows() {
			return maxOutstandingRows;
		}

		public void setMaxOutstandingRows(int maxOutstandingRows) {
			this.maxOutstandingRows = Math.max(1, Math.min(maxOutstandingRows, HARD_MAX_RESULT_ROWS));
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
