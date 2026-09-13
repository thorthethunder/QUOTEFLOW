import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PaymentApiService } from './payment-api.service';

describe('PaymentApiService', () => {
  let api: PaymentApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PaymentApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a payment without accepting client totals in the typed payload shape', () => {
    const invoiceId = '33333333-3333-3333-3333-333333333333';
    api
      .create(invoiceId, {
        amount: 100,
        paymentDate: '2026-09-13',
        paymentMethod: 'CASH',
        reference: 'RCPT-1',
        notes: 'Partial',
      })
      .subscribe((payment) => {
        expect(payment.amount).toBe(100);
        expect(payment.receiptNumber).toBe('RCP-000001');
      });

    const req = http.expectOne(
      (r) => r.url.endsWith(`/invoices/${invoiceId}/payments`) && r.method === 'POST',
    );
    expect(req.request.body.amount).toBe(100);
    expect(req.request.body.paymentMethod).toBe('CASH');
    expect(req.request.body.totalAmount).toBeUndefined();
    expect(req.request.body.amountPaid).toBeUndefined();
    expect(req.request.body.balanceDue).toBeUndefined();
    expect(req.request.body.paymentState).toBeUndefined();
    req.flush({
      id: '44444444-4444-4444-4444-444444444444',
      invoiceId,
      receiptNumber: 'RCP-000001',
      amount: 100,
      currency: 'INR',
      paymentDate: '2026-09-13',
      paymentMethod: 'CASH',
      reference: 'RCPT-1',
      notes: 'Partial',
      status: 'RECORDED',
      invoiceNumberSnapshot: 'INV-000001',
      invoiceTotalAtPayment: 265.5,
      previousPaidAmount: 0,
      remainingBalanceAfterPayment: 165.5,
      voidedAt: null,
      voidReason: null,
      createdAt: '2026-09-13T00:00:00Z',
      updatedAt: '2026-09-13T00:00:00Z',
    });
  });

  it('voids a payment with an optional reason', () => {
    const paymentId = '44444444-4444-4444-4444-444444444444';
    api.voidPayment(paymentId, { reason: 'Entered twice' }).subscribe((payment) => {
      expect(payment.status).toBe('VOIDED');
      expect(payment.voidReason).toBe('Entered twice');
    });

    const req = http.expectOne(
      (r) => r.url.endsWith(`/payments/${paymentId}/void`) && r.method === 'POST',
    );
    expect(req.request.body).toEqual({ reason: 'Entered twice' });
    req.flush({
      id: paymentId,
      invoiceId: '33333333-3333-3333-3333-333333333333',
      receiptNumber: 'RCP-000001',
      amount: 100,
      currency: 'INR',
      paymentDate: '2026-09-13',
      paymentMethod: 'CASH',
      reference: null,
      notes: null,
      status: 'VOIDED',
      invoiceNumberSnapshot: 'INV-000001',
      invoiceTotalAtPayment: 265.5,
      previousPaidAmount: 0,
      remainingBalanceAfterPayment: 165.5,
      voidedAt: '2026-09-13T12:00:00Z',
      voidReason: 'Entered twice',
      createdAt: '2026-09-13T00:00:00Z',
      updatedAt: '2026-09-13T12:00:00Z',
    });
  });

  it('downloads receipt PDF as a blob without putting tokens in the URL', () => {
    const paymentId = '44444444-4444-4444-4444-444444444444';
    api.downloadReceipt(paymentId).subscribe((blob) => {
      expect(blob.type).toBe('application/pdf');
      expect(blob.size).toBeGreaterThan(0);
    });

    const req = http.expectOne(
      (r) => r.url.endsWith(`/payments/${paymentId}/receipt.pdf`) && r.method === 'GET',
    );
    expect(req.request.responseType).toBe('blob');
    expect(req.request.urlWithParams.includes('accessToken')).toBeFalse();
    expect(req.request.urlWithParams.includes('token')).toBeFalse();
    req.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
  });
});
