import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EntitlementStore } from './entitlement.store';
import { EntitlementResponse } from './plan.models';

describe('EntitlementStore', () => {
  let store: EntitlementStore;
  let http: HttpTestingController;

  const entitlement: EntitlementResponse = {
    plan: 'FREE',
    planDisplayName: 'Free',
    status: 'ACTIVE',
    period: { from: '2026-09-01', to: '2026-09-30', timezone: 'Asia/Kolkata' },
    limits: {
      activeCustomers: { used: 5, limit: 5, unlimited: false },
      quotationsThisMonth: { used: 4, limit: 5, unlimited: false },
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
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(EntitlementStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('exposes create gates from loaded usage', () => {
    store.refresh().subscribe();
    http.expectOne((r) => r.url.endsWith('/subscription')).flush(entitlement);

    expect(store.canCreateCustomer()).toBeFalse();
    expect(store.canCreateQuotation()).toBeTrue();
    expect(store.canCreateInvoice()).toBeTrue();
  });

  it('maps PLAN_LIMIT_REACHED API errors', () => {
    const msg = store.messageFromApiError({
      error: {
        code: 'PLAN_LIMIT_REACHED',
        details: { feature: 'CUSTOMERS', used: 5, limit: 5, plan: 'FREE' },
      },
    });
    expect(msg).toContain('5 active customers');
  });
});
