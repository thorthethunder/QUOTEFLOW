export type QuotationStatus = 'DRAFT' | 'SENT' | 'CANCELLED';
export type DiscountType = 'NONE' | 'PERCENTAGE' | 'FIXED';

export interface QuotationItemInput {
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface QuotationItem {
  id: string;
  position: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineSubtotal: number;
}

export interface QuotationSummary {
  id: string;
  quotationNumber: string;
  customerDisplayName: string;
  customerCompanyName: string | null;
  issueDate: string;
  validUntil: string | null;
  status: QuotationStatus;
  currency: string;
  totalAmount: number;
  updatedAt: string;
}

export interface Quotation {
  id: string;
  customerId: string;
  quotationNumber: string;
  status: QuotationStatus;
  currency: string;
  issueDate: string;
  validUntil: string | null;
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
  items: QuotationItem[];
  convertedInvoiceId?: string | null;
  convertedInvoiceNumber?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PagedQuotations {
  content: QuotationSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface QuotationWritePayload {
  customerId: string;
  issueDate?: string | null;
  validUntil?: string | null;
  currency?: string | null;
  discountType: DiscountType;
  discountValue: number;
  taxRate: number;
  notes?: string | null;
  terms?: string | null;
  items: QuotationItemInput[];
  version?: number;
}

/** Client preview only — backend remains authoritative (HALF_UP, scale 2). */
export function previewTotals(
  items: QuotationItemInput[],
  discountType: DiscountType,
  discountValue: number,
  taxRate: number,
): { subtotal: number; discountAmount: number; taxAmount: number; totalAmount: number } {
  const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
  const subtotal = round2(
    items.reduce((sum, item) => sum + round2((Number(item.quantity) || 0) * (Number(item.unitPrice) || 0)), 0),
  );
  let discountAmount = 0;
  if (discountType === 'PERCENTAGE') {
    discountAmount = round2((subtotal * (Number(discountValue) || 0)) / 100);
  } else if (discountType === 'FIXED') {
    discountAmount = round2(Number(discountValue) || 0);
  }
  if (discountAmount > subtotal) {
    discountAmount = subtotal;
  }
  const taxable = round2(subtotal - discountAmount);
  const taxAmount = round2((taxable * (Number(taxRate) || 0)) / 100);
  return {
    subtotal,
    discountAmount,
    taxAmount,
    totalAmount: round2(taxable + taxAmount),
  };
}
