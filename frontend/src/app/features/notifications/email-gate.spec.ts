import { TestBed } from '@angular/core/testing';
import { EntitlementStore } from '../plan/entitlement.store';
import { PlanApiService } from '../plan/plan-api.service';
import { of } from 'rxjs';

describe('EntitlementStore email gate', () => {
  function entitlements(emailSending: boolean, plan: string) {
    return {
      getEntitlements: () =>
        of({
          plan,
          planDisplayName: plan === 'FREE' ? 'Free' : 'Pro',
          status: 'ACTIVE',
          period: { from: '', to: '', timezone: 'UTC' },
          limits: {
            activeCustomers: { used: 0, limit: 5, unlimited: false },
            quotationsThisMonth: { used: 0, limit: 5, unlimited: false },
            invoicesThisMonth: { used: 0, limit: 5, unlimited: false },
          },
          features: {
            removeQuoteFlowBranding: false,
            multiUser: false,
            advancedReports: false,
            emailSending,
            aiAssistant: false,
          },
          billingCheckoutAvailable: false,
        }),
    };
  }

  it('FREE plan cannot send email', () => {
    TestBed.configureTestingModule({
      providers: [EntitlementStore, { provide: PlanApiService, useValue: entitlements(false, 'FREE') }],
    });
    const store = TestBed.inject(EntitlementStore);
    store.refresh().subscribe();
    expect(store.canSendEmail()).toBeFalse();
  });

  it('PRO plan can send email', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [EntitlementStore, { provide: PlanApiService, useValue: entitlements(true, 'PRO') }],
    });
    const store = TestBed.inject(EntitlementStore);
    store.refresh().subscribe();
    expect(store.canSendEmail()).toBeTrue();
  });
});
