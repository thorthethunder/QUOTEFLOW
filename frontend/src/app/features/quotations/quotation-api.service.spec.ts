import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuotationApiService } from './quotation-api.service';
import { previewTotals } from './quotation.models';

describe('QuotationApiService', () => {
  let api: QuotationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(QuotationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a quotation without accepting client totals in the typed payload shape', () => {
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
      .subscribe((q) => expect(q.totalAmount).toBe(265.5));

    const req = http.expectOne((r) => r.url.endsWith('/quotations') && r.method === 'POST');
    expect(req.request.body.totalAmount).toBeUndefined();
    req.flush({
      id: '22222222-2222-2222-2222-222222222222',
      customerId: '11111111-1111-1111-1111-111111111111',
      quotationNumber: 'Q-000001',
      status: 'DRAFT',
      currency: 'INR',
      issueDate: '2026-09-13',
      validUntil: null,
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
      businessName: 'Phase6 Co',
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

  it('downloads PDF as a blob without putting tokens in the URL', () => {
    api.downloadPdf('22222222-2222-2222-2222-222222222222').subscribe((blob) => {
      expect(blob.type).toBe('application/pdf');
      expect(blob.size).toBeGreaterThan(0);
    });

    const req = http.expectOne(
      (r) => r.url.endsWith('/quotations/22222222-2222-2222-2222-222222222222/pdf') && r.method === 'GET',
    );
    expect(req.request.responseType).toBe('blob');
    expect(req.request.urlWithParams.includes('accessToken')).toBeFalse();
    req.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
  });
});

describe('previewTotals', () => {
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
