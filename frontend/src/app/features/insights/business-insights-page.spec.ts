import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { BusinessInsightsPageComponent } from './business-insights-page';
import { ReportingInsightResponse } from './business-insights.models';

describe('BusinessInsightsPageComponent', () => {
  let fixture: ComponentFixture<BusinessInsightsPageComponent>;
  let http: HttpTestingController;

  const response: ReportingInsightResponse = {
    answer: '<img src=x onerror=alert(1)> Collections increased in INR and decreased in USD.',
    insightType: 'PERIOD_COMPARISON',
    period: { label: 'THIS_MONTH', from: '2026-09-01', to: '2026-09-30', timezone: 'Asia/Kolkata' },
    comparisonPeriod: {
      label: 'PREVIOUS_PERIOD',
      from: '2026-08-01',
      to: '2026-08-31',
      timezone: 'Asia/Kolkata',
    },
    metrics: dashboard(),
    comparisonMetrics: dashboard(),
    facts: [
      {
        metric: 'COLLECTED',
        currency: 'INR',
        currentValue: 85000,
        previousValue: 72000,
        absoluteChange: 13000,
        percentageChange: 18.06,
        comparisonReason: 'OK',
      },
      {
        metric: 'COLLECTED',
        currency: 'USD',
        currentValue: 600,
        previousValue: 900,
        absoluteChange: -300,
        percentageChange: -33.33,
        comparisonReason: 'OK',
      },
      {
        metric: 'OUTSTANDING',
        currency: 'INR',
        currentValue: 5000,
        previousValue: 0,
        absoluteChange: 5000,
        percentageChange: null,
        comparisonReason: 'NO_PREVIOUS_BASE',
      },
    ],
    topOutstandingInvoices: [
      {
        id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        invoiceNumber: 'INV-0015',
        customerId: '11111111-2222-3333-4444-555555555555',
        customerDisplayName: 'Raj Electrical',
        issueDate: '2026-09-02',
        dueDate: '2026-09-12',
        currency: 'INR',
        totalAmount: 12000,
        amountPaid: 0,
        balanceDue: 12000,
        paymentState: 'UNPAID',
      },
    ],
    customerOutstanding: [
      {
        customerId: '11111111-2222-3333-4444-555555555555',
        customerDisplayName: 'Raj Electrical',
        currency: 'INR',
        outstandingAmount: 12000,
        currencyOutstandingTotal: 24000,
        concentrationPercent: 50,
      },
    ],
    references: [
      {
        type: 'INVOICE',
        id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        displayNumber: 'INV-0015',
        label: 'INV-0015',
      },
      {
        type: 'CUSTOMER',
        id: '11111111-2222-3333-4444-555555555555',
        displayNumber: null,
        label: 'Raj Electrical',
      },
    ],
    warnings: ['AI summary is temporarily unavailable. Authoritative metrics are still shown.'],
    aiNarrativeAvailable: false,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusinessInsightsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(BusinessInsightsPageComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders currency-separated metrics, zero-base copy, warnings, and trusted links', () => {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url.endsWith('/ai/insights/analyze'));
    req.flush(response);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('INR');
    expect(text).toContain('USD');
    expect(text).toContain('Up INR 13000.00');
    expect(text).toContain('Down USD 300.00');
    expect(text).toContain('No previous base');
    expect(text).toContain('AI summary is temporarily unavailable');
    expect(text).toContain('INV-0015');
    expect(text).not.toContain('85600');

    const answer = fixture.nativeElement.querySelector('.answer-text') as HTMLElement;
    expect(answer.textContent).toContain('<img');
    expect(answer.querySelector('img')).toBeNull();

    const invoiceLink = fixture.debugElement.query(By.css('a[href*="/app/invoices/"]'));
    expect(invoiceLink.attributes['href']).toContain('/app/invoices/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee');
  });

  it('submits selected suggestion and surfaces rate limit', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/ai/insights/analyze')).flush(response);
    fixture.detectChanges();

    const buttons = fixture.debugElement.queryAll(By.css('.suggestions button'));
    buttons[1].nativeElement.click();
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url.endsWith('/ai/insights/analyze'));
    expect(req.request.body.question).toBe('Compare this month with last month.');
    req.flush({ code: 'AI_RATE_LIMITED' }, { status: 429, statusText: 'Too Many Requests' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Too many insight requests');
  });
});

function dashboard(): ReportingInsightResponse['metrics'] {
  return {
    from: '2026-09-01',
    to: '2026-09-30',
    timezone: 'Asia/Kolkata',
    defaultPeriodLabel: 'this_month',
    customers: { activeCount: 1, archivedCount: 0, newInPeriodCount: 0 },
    quotations: {
      draftCount: 0,
      sentCount: 0,
      cancelledCount: 0,
      convertedCount: 0,
      quotedAmountByCurrency: [],
    },
    invoices: {
      sentCount: 1,
      draftCount: 0,
      cancelledCount: 0,
      unpaidCount: 1,
      partiallyPaidCount: 0,
      paidCount: 0,
      invoicedAmountByCurrency: [{ currency: 'INR', amount: 12000 }],
      outstandingAmountByCurrency: [{ currency: 'INR', amount: 12000 }],
    },
    payments: { recordedCount: 0, collectedAmountByCurrency: [] },
    collectionsSeries: [],
    recentInvoices: [],
    recentPayments: [],
  };
}
