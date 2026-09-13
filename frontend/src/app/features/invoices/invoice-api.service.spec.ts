import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InvoiceApiService } from './invoice-api.service';
import { previewTotals } from './invoice.models';

describe('InvoiceApiService', () => {
  let api: InvoiceApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(InvoiceApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates an invoice without accepting client totals in the typed payload shape', () => {
    api
      .create({
        customerId: '11111111-1111-1111-1111-111111111111',
        discountType: 'PERCENTAGE',
        discountValue: 10,
        taxRate: 18,
        items: [
          { description: 'A', quantity: 2, unitPrice: 100 },
          { description: 'B', quantity: 1, unitPrice: 50 },
        ],
      })
      .subscribe((invoice) => expect(invoice.totalAmount).toBe(265.5));

    const req = http.expectOne((r) => r.url.endsWith('/invoices') && r.method === 'POST');
    expect(req.request.body.totalAmount).toBeUndefined();
    req.flush({
      id: '33333333-3333-3333-3333-333333333333',
      customerId: '11111111-1111-1111-1111-111111111111',
      sourceQuotationId: null,
      sourceQuotationNumber: null,
      invoiceNumber: 'INV-000001',
      status: 'DRAFT',
      currency: 'INR',
      issueDate: '2026-09-13',
      dueDate: null,
      customerDisplayName: 'Ada',
      customerCompanyName: null,
      customerEmail: null,
      customerPhone: null,
      customerAddressLine1: null,
      customerAddressLine2: null,
      customerCity: null,
      customerStateRegion: null,
      customerPostalCode: null,
      customerCountryCode: null,
      customerTaxId: null,
      businessName: 'Phase8 Co',
      businessEmail: null,
      businessPhone: null,
      businessAddressLine1: null,
      businessAddressLine2: null,
      businessCity: null,
      businessStateRegion: null,
      businessPostalCode: null,
      businessCountryCode: null,
      businessTaxId: null,
      notes: null,
      terms: null,
      discountType: 'PERCENTAGE',
      discountValue: 10,
      taxRate: 18,
      subtotal: 250,
      discountAmount: 25,
      taxAmount: 40.5,
      totalAmount: 265.5,
      version: 0,
      items: [],
      createdAt: '2026-09-13T00:00:00Z',
      updatedAt: '2026-09-13T00:00:00Z',
    });
  });

  it('converts a quotation to an invoice via the quotations convert endpoint', () => {
    const quotationId = '22222222-2222-2222-2222-222222222222';
    api.convertFromQuotation(quotationId).subscribe((invoice) => {
      expect(invoice.invoiceNumber).toBe('INV-000001');
      expect(invoice.sourceQuotationId).toBe(quotationId);
    });

    const req = http.expectOne(
      (r) =>
        r.url.endsWith(`/quotations/${quotationId}/convert-to-invoice`) && r.method === 'POST',
    );
    req.flush({
      id: '33333333-3333-3333-3333-333333333333',
      customerId: '11111111-1111-1111-1111-111111111111',
      sourceQuotationId: quotationId,
      sourceQuotationNumber: 'Q-000001',
      invoiceNumber: 'INV-000001',
      status: 'DRAFT',
      currency: 'INR',
      issueDate: '2026-09-13',
      dueDate: null,
      customerDisplayName: 'Ada',
      customerCompanyName: null,
      customerEmail: null,
      customerPhone: null,
      customerAddressLine1: null,
      customerAddressLine2: null,
      customerCity: null,
      customerStateRegion: null,
      customerPostalCode: null,
      customerCountryCode: null,
      customerTaxId: null,
      businessName: 'Phase8 Co',
      businessEmail: null,
      businessPhone: null,
      businessAddressLine1: null,
      businessAddressLine2: null,
      businessCity: null,
      businessStateRegion: null,
      businessPostalCode: null,
      businessCountryCode: null,
      businessTaxId: null,
      notes: null,
      terms: null,
      discountType: 'NONE',
      discountValue: 0,
      taxRate: 0,
      subtotal: 100,
      discountAmount: 0,
      taxAmount: 0,
      totalAmount: 100,
      version: 0,
      items: [],
      createdAt: '2026-09-13T00:00:00Z',
      updatedAt: '2026-09-13T00:00:00Z',
    });
  });
});

describe('previewTotals (invoice)', () => {
  it('matches the documented financial example for UX preview', () => {
    const preview = previewTotals(
      [
        { description: 'A', quantity: 2, unitPrice: 100 },
        { description: 'B', quantity: 1, unitPrice: 50 },
      ],
      'PERCENTAGE',
      10,
      18,
    );
    expect(preview.subtotal).toBe(250);
    expect(preview.discountAmount).toBe(25);
    expect(preview.taxAmount).toBe(40.5);
    expect(preview.totalAmount).toBe(265.5);
  });
});
