export type PlanId = 'FREE' | 'PRO' | 'BUSINESS';
export type BillingInterval = 'MONTHLY' | 'ANNUAL';

export interface UsageMeter {
  used: number;
  limit: number | null;
  unlimited: boolean;
}

export interface EntitlementLimits {
  activeCustomers: UsageMeter;
  quotationsThisMonth: UsageMeter;
  invoicesThisMonth: UsageMeter;
}

export interface FeatureFlags {
  removeQuoteFlowBranding: boolean;
  multiUser: boolean;
  advancedReports: boolean;
  emailSending: boolean;
  aiAssistant: boolean;
}

export interface EntitlementPeriod {
  from: string;
  to: string;
  timezone: string;
}

export interface EntitlementResponse {
  plan: PlanId;
  planDisplayName: string;
  status: string;
  period: EntitlementPeriod;
  limits: EntitlementLimits;
  features: FeatureFlags;
  billingCheckoutAvailable: boolean;
  billingInterval?: BillingInterval | null;
  billingPeriodStart?: string | null;
  billingPeriodEnd?: string | null;
  cancelAtPeriodEnd?: boolean;
  billingProvider?: string | null;
  providerStatus?: string | null;
  availableBillingIntervals?: BillingInterval[];
}

export interface PlanCatalogItem {
  id: PlanId;
  displayName: string;
  activeCustomerLimit: number | null;
  quotationsPerMonth: number | null;
  invoicesPerMonth: number | null;
  removeQuoteFlowBranding: boolean;
  multiUser: boolean;
  monthlyPriceDisplay: string;
  yearlyPriceDisplay: string | null;
  billingAvailable?: boolean;
  billingIntervals?: BillingInterval[];
}

export interface CheckoutResponse {
  provider: string;
  keyId: string;
  subscriptionId: string;
  plan: PlanId;
  billingInterval: BillingInterval;
}

export interface VerifyCheckoutResponse {
  plan: PlanId;
  status: string;
  providerStatus: string | null;
  billingInterval: BillingInterval | null;
  activated: boolean;
}

/** Centralized display catalog (mirrors backend PlanCatalog). */
export const PLAN_DISPLAY = {
  FREE: { name: 'Free', monthly: '₹0', yearly: '₹0' },
  PRO: { name: 'Pro', monthly: '₹199/month', yearly: '₹1,999/year', annualSave: 389 },
  BUSINESS: { name: 'Business', monthly: '₹499/month', yearly: null as string | null },
} as const;

export function formatUsage(meter: UsageMeter): string {
  if (meter.unlimited || meter.limit == null) {
    return `${meter.used} used (unlimited)`;
  }
  return `${meter.used} of ${meter.limit}`;
}

export function planLimitMessage(details: {
  feature?: string;
  used?: number;
  limit?: number;
  plan?: string;
}): string {
  const feature = details.feature ?? 'FEATURE';
  const limit = details.limit ?? '?';
  const plan = details.plan ?? 'FREE';
  switch (feature) {
    case 'CUSTOMERS':
      return `You've reached the ${plan} plan limit of ${limit} active customers.`;
    case 'QUOTATIONS_MONTHLY':
      return `You've reached the ${plan} plan limit of ${limit} quotations this month.`;
    case 'INVOICES_MONTHLY':
      return `You've reached the ${plan} plan limit of ${limit} invoices this month.`;
    default:
      return `You've reached a ${plan} plan limit (${feature}).`;
  }
}
