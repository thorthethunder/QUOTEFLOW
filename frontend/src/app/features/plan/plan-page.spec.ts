import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { PlanPageComponent } from './plan-page';
import { EntitlementResponse, PlanCatalogItem, planLimitMessage } from './plan.models';
import { AuthService } from '../../core/auth/auth.service';
import { signal } from '@angular/core';

describe('planLimitMessage', () => {
  it('formats customer limit message', () => {
    expect(
      planLimitMessage({ feature: 'CUSTOMERS', used: 5, limit: 5, plan: 'FREE' }),
    ).toContain('5 active customers');
  });
});

describe('PlanPageComponent', () => {
  let http: HttpTestingController;

  const entitlement: EntitlementResponse = {
    plan: 'FREE',
    planDisplayName: 'Free',
    status: 'ACTIVE',
    period: { from: '2026-09-01', to: '2026-09-30', timezone: 'Asia/Kolkata' },
    limits: {
      activeCustomers: { used: 3, limit: 5, unlimited: false },
      quotationsThisMonth: { used: 2, limit: 5, unlimited: false },
      invoicesThisMonth: { used: 1, limit: 5, unlimited: false },
    },
    features: {
      removeQuoteFlowBranding: false,
      multiUser: false,
      advancedReports: false,
      emailSending: false,
      aiAssistant: false,
    },
    billingCheckoutAvailable: false,
    availableBillingIntervals: [],
  };

  const catalog: PlanCatalogItem[] = [
    {
      id: 'FREE',
      displayName: 'Free',
      activeCustomerLimit: 5,
      quotationsPerMonth: 5,
      invoicesPerMonth: 5,
      removeQuoteFlowBranding: false,
      multiUser: false,
      monthlyPriceDisplay: '₹0',
      yearlyPriceDisplay: '₹0',
      billingAvailable: false,
      billingIntervals: [],
    },
    {
      id: 'PRO',
      displayName: 'Pro',
      activeCustomerLimit: null,
      quotationsPerMonth: null,
      invoicesPerMonth: null,
      removeQuoteFlowBranding: true,
      multiUser: false,
      monthlyPriceDisplay: '₹199/month',
      yearlyPriceDisplay: '₹1,999/year',
      billingAvailable: false,
      billingIntervals: [],
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlanPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({
              userId: 'u1',
              businessId: 'b1',
              businessName: 'Test',
              firstName: 'A',
              lastName: 'B',
              email: 'a@example.com',
              tenantRole: 'OWNER' as const,
            }),
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders current plan and shows upgrade unavailable when billing is off', () => {
    const fixture = TestBed.createComponent(PlanPageComponent);
    fixture.detectChanges();

    http.expectOne((r) => r.url.endsWith('/subscription')).flush(entitlement);
    http.expectOne((r) => r.url.endsWith('/plans')).flush(catalog);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Free');
    expect(text).toContain('3 of 5');
    expect(text).toContain('Upgrade unavailable');
    expect(text).not.toContain('Buy now');
    expect(text).not.toContain('(ACTIVE)');
  });

  it('shows Upgrade to Pro when billing is available', () => {
    const fixture = TestBed.createComponent(PlanPageComponent);
    fixture.detectChanges();

    http.expectOne((r) => r.url.endsWith('/subscription')).flush({
      ...entitlement,
      billingCheckoutAvailable: true,
      availableBillingIntervals: ['MONTHLY', 'ANNUAL'],
    });
    http.expectOne((r) => r.url.endsWith('/plans')).flush([
      catalog[0],
      { ...catalog[1], billingAvailable: true, billingIntervals: ['MONTHLY', 'ANNUAL'] },
    ]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Upgrade to Pro');
    expect(text).toContain('Monthly');
    expect(text).toContain('Annual');
  });
});
