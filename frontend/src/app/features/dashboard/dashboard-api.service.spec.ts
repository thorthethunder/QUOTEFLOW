import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardApiService } from './dashboard-api.service';
import { DashboardSummary } from './dashboard.models';

describe('DashboardApiService', () => {
  let api: DashboardApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(DashboardApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests summary with from/to params', () => {
    const mock: Partial<DashboardSummary> = {
      from: '2026-09-01',
      to: '2026-09-30',
      invoices: {
        sentCount: 1,
        draftCount: 0,
        cancelledCount: 0,
        unpaidCount: 0,
        partiallyPaidCount: 0,
        paidCount: 1,
        invoicedAmountByCurrency: [{ currency: 'INR', amount: 100 }],
        outstandingAmountByCurrency: [{ currency: 'INR', amount: 0 }],
      },
    };

    api.summary('2026-09-01', '2026-09-30').subscribe((s) => {
      expect(s.from).toBe('2026-09-01');
      expect(s.invoices.invoicedAmountByCurrency[0].currency).toBe('INR');
    });

    const req = http.expectOne(
      (r) =>
        r.url.endsWith('/dashboard/summary') &&
        r.method === 'GET' &&
        r.params.get('from') === '2026-09-01' &&
        r.params.get('to') === '2026-09-30',
    );
    req.flush(mock);
  });

  it('omits params for default period', () => {
    api.summary().subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/dashboard/summary') && r.method === 'GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush({ from: '2026-09-01', to: '2026-09-30' });
  });
});
