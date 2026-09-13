export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'UPI_MANUAL' | 'CHEQUE' | 'OTHER';

export type PaymentRecordStatus = 'RECORDED' | 'VOIDED';

export type InvoicePaymentState = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID';

export interface PaymentSummary {
  amountPaid: number;
  balanceDue: number;
  paymentState: InvoicePaymentState;
}

export interface Payment {
  id: string;
  invoiceId: string;
  receiptNumber: string;
  amount: number;
  currency: string;
  paymentDate: string;
  paymentMethod: PaymentMethod;
  reference: string | null;
  notes: string | null;
  status: PaymentRecordStatus;
  invoiceNumberSnapshot: string | null;
  invoiceTotalAtPayment: number | null;
  previousPaidAmount: number | null;
  remainingBalanceAfterPayment: number | null;
  voidedAt: string | null;
  voidReason: string | null;
  createdAt: string;
  updatedAt: string;
  invoicePaymentSummary?: PaymentSummary | null;
}

export interface InvoicePaymentsResponse {
  summary: PaymentSummary;
  payments: Payment[];
}

/** Create payload — amount and method only; no client totals. */
export interface CreatePaymentPayload {
  amount: number;
  paymentDate?: string | null;
  paymentMethod: PaymentMethod;
  reference?: string | null;
  notes?: string | null;
}

export interface VoidPaymentPayload {
  reason?: string | null;
}

export const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'UPI_MANUAL', label: 'UPI (manual)' },
  { value: 'CHEQUE', label: 'Cheque' },
  { value: 'OTHER', label: 'Other' },
];

export function paymentStateLabel(state: InvoicePaymentState | null | undefined): string {
  switch (state) {
    case 'PAID':
      return 'Paid';
    case 'PARTIALLY_PAID':
      return 'Partially paid';
    case 'UNPAID':
      return 'Unpaid';
    default:
      return '—';
  }
}

export function paymentMethodLabel(method: PaymentMethod): string {
  return PAYMENT_METHODS.find((m) => m.value === method)?.label ?? method;
}

export function paymentStatusLabel(status: PaymentRecordStatus): string {
  return status === 'VOIDED' ? 'Voided' : 'Recorded';
}
