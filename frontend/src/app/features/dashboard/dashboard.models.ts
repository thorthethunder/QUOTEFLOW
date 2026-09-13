export interface MoneyByCurrency {
  currency: string;
  amount: number;
}

export interface CustomerMetrics {
  activeCount: number;
  archivedCount: number;
  newInPeriodCount: number;
}

export interface QuotationMetrics {
  draftCount: number;
  sentCount: number;
  cancelledCount: number;
  convertedCount: number;
  quotedAmountByCurrency: MoneyByCurrency[];
}

export interface InvoiceMetrics {
  sentCount: number;
  draftCount: number;
  cancelledCount: number;
  unpaidCount: number;
  partiallyPaidCount: number;
  paidCount: number;
  invoicedAmountByCurrency: MoneyByCurrency[];
  outstandingAmountByCurrency: MoneyByCurrency[];
}

export interface PaymentMetrics {
  recordedCount: number;
  collectedAmountByCurrency: MoneyByCurrency[];
}

export interface CollectionsSeriesPoint {
  periodStart: string;
  granularity: 'DAILY' | 'MONTHLY' | string;
  currency: string;
  amount: number;
}

export interface RecentInvoice {
  id: string;
  invoiceNumber: string;
  customerDisplayName: string;
  issueDate: string;
  status: string;
  paymentState: string | null;
  currency: string;
  totalAmount: number;
}

export interface RecentPayment {
  id: string;
  receiptNumber: string;
  invoiceNumber: string;
  invoiceId: string;
  paymentDate: string;
  paymentMethod: string;
  currency: string;
  amount: number;
}

export interface DashboardSummary {
  from: string;
  to: string;
  timezone: string;
  defaultPeriodLabel: string;
  customers: CustomerMetrics;
  quotations: QuotationMetrics;
  invoices: InvoiceMetrics;
  payments: PaymentMetrics;
  collectionsSeries: CollectionsSeriesPoint[];
  recentInvoices: RecentInvoice[];
  recentPayments: RecentPayment[];
}

export type PeriodPreset = 'this_month' | 'last_30' | 'this_year' | 'custom';

export function paymentStateLabel(state: string | null | undefined): string {
  switch (state) {
    case 'UNPAID':
      return 'Unpaid';
    case 'PARTIALLY_PAID':
      return 'Partially paid';
    case 'PAID':
      return 'Paid';
    default:
      return '—';
  }
}
