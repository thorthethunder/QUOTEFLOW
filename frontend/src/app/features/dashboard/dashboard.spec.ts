import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard';
import { DashboardSummary } from './dashboard.models';

describe('DashboardComponent', () => {
  let http: HttpTestingController;

  const summaryFixture: DashboardSummary = {
    from: '2026-09-01',
    to: '2026-09-30',
    timezone: 'Asia/Kolkata',
    defaultPeriodLabel: 'this_month',
    customers: { activeCount: 2, archivedCount: 0, newInPeriodCount: 1 },
    quotations: {
      draftCount: 0,
      sentCount: 1,
      cancelledCount: 0,
      convertedCount: 0,
      quotedAmountByCurrency: [{ currency: 'INR', amount: 500 }],
    },
    invoices: {
      sentCount: 2,
      draftCount: 0,
      cancelledCount: 0,
      unpaidCount: 0,
      partiallyPaidCount: 1,
      paidCount: 1,
      invoicedAmountByCurrency: [
        { currency: 'INR', amount: 1500 },
        { currency: 'USD', amount: 200 },
      ],
      outstandingAmountByCurrency: [{ currency: 'INR', amount: 600 }],
    },
    payments: {
      recordedCount: 2,
      collectedAmountByCurrency: [
        { currency: 'INR', amount: 900 },
        { currency: 'USD', amount: 200 },
      ],
    },
    collectionsSeries: [
      { periodStart: '2026-09-13', granularity: 'DAILY', currency: 'INR', amount: 400 },
    ],
    recentInvoices: [],
    recentPayments: [],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads summary and shows currency-grouped amounts without combining', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    http.expectOne((r) => r.url.endsWith('/subscription')).flush({
      plan: 'FREE',
      planDisplayName: 'Free',
      status: 'ACTIVE',
      period: { from: '2026-09-01', to: '2026-09-30', timezone: 'Asia/Kolkata' },
      limits: {
        activeCustomers: { used: 0, limit: 5, unlimited: false },
        quotationsThisMonth: { used: 0, limit: 5, unlimited: false },
        invoicesThisMonth: { used: 0, limit: 5, unlimited: false },
      },
      features: {
        removeQuoteFlowBranding: false,
        multiUser: false,
        advancedReports: false,
        emailSending: false,
        aiAssistant: false,
      },
      billingCheckoutAvailable: false,
    });

    const req = http.expectOne((r) => r.url.endsWith('/dashboard/summary'));
    req.flush(summaryFixture);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Invoiced');
    expect(text).toContain('INR 1500.00');
    expect(text).toContain('USD 200.00');
    expect(text).toContain('Outstanding');
    expect(text).toContain('Partially paid');
    expect(text).toContain('Plan: Free');
    expect(text).not.toContain('1700.00');
  });

  it('shows error and retry when summary fails', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    http.expectOne((r) => r.url.endsWith('/subscription')).flush({
      plan: 'FREE',
      planDisplayName: 'Free',
      status: 'ACTIVE',
      period: { from: '2026-09-01', to: '2026-09-30', timezone: 'Asia/Kolkata' },
      limits: {
        activeCustomers: { used: 0, limit: 5, unlimited: false },
        quotationsThisMonth: { used: 0, limit: 5, unlimited: false },
        invoicesThisMonth: { used: 0, limit: 5, unlimited: false },
      },
      features: {
        removeQuoteFlowBranding: false,
        multiUser: false,
        advancedReports: false,
        emailSending: false,
        aiAssistant: false,
      },
      billingCheckoutAvailable: false,
    });

    const req = http.expectOne((r) => r.url.endsWith('/dashboard/summary'));
    req.flush({ message: 'fail' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Could not load dashboard');
    expect(text).toContain('Retry');
  });
});
