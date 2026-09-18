import { DashboardSummary, MoneyByCurrency } from '../dashboard/dashboard.models';

export type InsightType =
  | 'BUSINESS_SUMMARY'
  | 'PERIOD_COMPARISON'
  | 'COLLECTIONS_COMPARISON'
  | 'OUTSTANDING_ANALYSIS'
  | 'TOP_OUTSTANDING_INVOICES'
  | 'CUSTOMER_OUTSTANDING_ANALYSIS'
  | 'PAYMENT_STATUS_ANALYSIS'
  | string;

export interface InsightPeriod {
  label: string;
  from: string;
  to: string;
  timezone: string;
}

export interface InsightFact {
  metric: string;
  currency: string;
  currentValue: number;
  previousValue: number;
  absoluteChange: number;
  percentageChange: number | null;
  comparisonReason: string;
}

export interface InsightReference {
  type: 'CUSTOMER' | 'QUOTATION' | 'INVOICE' | 'PAYMENT' | string;
  id: string;
  displayNumber: string | null;
  label: string | null;
}

export interface InsightOutstandingInvoice {
  id: string;
  invoiceNumber: string;
  customerId: string;
  customerDisplayName: string;
  issueDate: string;
  dueDate: string | null;
  currency: string;
  totalAmount: number;
  amountPaid: number;
  balanceDue: number;
  paymentState: string;
}

export interface InsightCustomerOutstanding {
  customerId: string;
  customerDisplayName: string;
  currency: string;
  outstandingAmount: number;
  currencyOutstandingTotal: number;
  concentrationPercent: number | null;
}

export interface ReportingInsightResponse {
  answer: string;
  insightType: InsightType;
  period: InsightPeriod;
  comparisonPeriod: InsightPeriod;
  metrics: DashboardSummary;
  comparisonMetrics: DashboardSummary;
  facts: InsightFact[];
  topOutstandingInvoices: InsightOutstandingInvoice[];
  customerOutstanding: InsightCustomerOutstanding[];
  references: InsightReference[];
  warnings: string[];
  aiNarrativeAvailable: boolean;
}

export interface CurrencyInsightGroup {
  currency: string;
  collected?: InsightFact;
  invoiced?: InsightFact;
  outstanding?: InsightFact;
  currentAmounts: MoneyByCurrency[];
}
