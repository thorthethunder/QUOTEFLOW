export interface AiUsageFeature {
  feature: string;
  entitled: boolean;
  used: number;
  limit: number;
  remaining: number;
  period: string;
  resetAt: string;
}

export interface AiUsageSummaryResponse {
  features: AiUsageFeature[];
}

export const AI_FEATURE_LABELS: Record<string, string> = {
  QUOTE_DRAFT: 'Quote drafts',
  BUSINESS_COPILOT: 'Business Copilot',
  REPORTING_INSIGHT: 'Reporting insights',
  PAYMENT_REMINDER: 'Payment reminders',
  KNOWLEDGE_INGESTION: 'Knowledge ingestion',
  KNOWLEDGE_QUERY: 'Knowledge Q&A',
  AGENT_WORKFLOW: 'Agent workflows',
};
