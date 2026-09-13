export interface CustomerSummary {
  id: string;
  displayName: string;
  companyName: string | null;
  email: string | null;
  phone: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  updatedAt: string;
}

export interface Customer {
  id: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  stateRegion: string | null;
  postalCode: string | null;
  countryCode: string | null;
  taxId: string | null;
  notes: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  updatedAt: string;
}

export interface PagedCustomers {
  content: CustomerSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CustomerWritePayload {
  displayName: string;
  email?: string | null;
  phone?: string | null;
  companyName?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateRegion?: string | null;
  postalCode?: string | null;
  countryCode?: string | null;
  taxId?: string | null;
  notes?: string | null;
}

export interface CustomerListParams {
  q?: string;
  status?: 'ACTIVE' | 'ARCHIVED';
  page?: number;
  size?: number;
  sort?: string;
}
