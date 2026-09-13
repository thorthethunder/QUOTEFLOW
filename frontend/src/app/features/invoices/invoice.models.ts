import {
  InvoicePaymentState,
  PaymentSummary,
} from '../payments/payment.models';
import { DiscountType, QuotationItemInput, previewTotals } from '../quotations/quotation.models';

export type { DiscountType };
export { previewTotals };
export type InvoiceStatus = 'DRAFT' | 'SENT' | 'CANCELLED';

export type InvoiceItemInput = QuotationItemInput;

export interface InvoiceItem {
  id: string;
  position: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineSubtotal: number;
}

export interface InvoiceSummary {
  id: string;
  invoiceNumber: string;
  customerDisplayName: string;
  customerCompanyName: string | null;
  issueDate: string;
  dueDate: string | null;
  status: InvoiceStatus;
  currency: string;
  totalAmount: number;
  sourceQuotationId: string | null;
  updatedAt: string;
  paymentState?: InvoicePaymentState;
  amountPaid?: number;
  balanceDue?: number;
}

export interface Invoice {
  id: string;
  customerId: string;
  sourceQuotationId: string | null;
  sourceQuotationNumber: string | null;
  invoiceNumber: string;
  status: InvoiceStatus;
  currency: string;
  issueDate: string;
  dueDate: string | null;
  customerDisplayName: string;
  customerCompanyName: string | null;
  customerEmail: string | null;
  customerPhone: string | null;
  customerAddressLine1: string | null;
  customerAddressLine2: string | null;
  customerCity: string | null;
  customerStateRegion: string | null;
  customerPostalCode: string | null;
  customerCountryCode: string | null;
  customerTaxId: string | null;
  businessName: string;
  businessEmail: string | null;
  businessPhone: string | null;
  businessAddressLine1: string | null;
  businessAddressLine2: string | null;
  businessCity: string | null;
  businessStateRegion: string | null;
  businessPostalCode: string | null;
  businessCountryCode: string | null;
  businessTaxId: string | null;
  notes: string | null;
  terms: string | null;
  discountType: DiscountType;
  discountValue: number;
  taxRate: number;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  version: number;
  items: InvoiceItem[];
  paymentSummary?: PaymentSummary;
  createdAt: string;
  updatedAt: string;
}

export interface PagedInvoices {
  content: InvoiceSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface InvoiceWritePayload {
  customerId: string;
  issueDate?: string | null;
  dueDate?: string | null;
  currency?: string | null;
  discountType: DiscountType;
  discountValue: number;
  taxRate: number;
  notes?: string | null;
  terms?: string | null;
  items: InvoiceItemInput[];
  version?: number;
}
