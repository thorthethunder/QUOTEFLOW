import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { PlanApiService } from './plan-api.service';
import { BillingApiService } from './billing-api.service';
import { EntitlementStore } from './entitlement.store';
import { RazorpayCheckoutLoader } from './razorpay-checkout.loader';
import {
  BillingInterval,
  EntitlementResponse,
  PlanCatalogItem,
  PlanId,
  PLAN_DISPLAY,
  UsageMeter,
  formatUsage,
} from './plan.models';

@Component({
  selector: 'app-plan-page',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './plan-page.html',
  styleUrl: './plan-page.scss',
})
export class PlanPageComponent implements OnInit {
  private readonly api = inject(PlanApiService);
  private readonly billingApi = inject(BillingApiService);
  private readonly store = inject(EntitlementStore);
  private readonly auth = inject(AuthService);
  private readonly razorpay = inject(RazorpayCheckoutLoader);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly entitlements = signal<EntitlementResponse | null>(null);
  readonly catalog = signal<PlanCatalogItem[]>([]);
  readonly selectedInterval = signal<BillingInterval>('MONTHLY');
  readonly checkoutBusy = signal(false);
  readonly checkoutMessage = signal<string | null>(null);
  readonly cancelBusy = signal(false);

  readonly formatUsage = formatUsage;
  readonly planDisplay = PLAN_DISPLAY;

  ngOnInit(): void {
    this.reload();
  }

  get isOwner(): boolean {
    return this.auth.currentUser()?.tenantRole === 'OWNER';
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.store.refresh().subscribe((ent) => {
      this.entitlements.set(ent);
      if (ent?.availableBillingIntervals?.length) {
        this.selectedInterval.set(ent.availableBillingIntervals[0]);
      }
      this.api.getCatalog().subscribe({
        next: (items) => {
          this.catalog.set(items);
          this.loading.set(false);
          if (!ent) {
            this.error.set('Could not load plan usage.');
          }
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Could not load plan catalog.');
        },
      });
    });
  }

  limitLabel(limit: number | null): string {
    return limit == null ? 'Unlimited' : String(limit);
  }

  isCurrent(planId: string): boolean {
    return this.entitlements()?.plan === planId;
  }

  isUpgradeTarget(target: string, current: PlanId): boolean {
    const rank: Record<PlanId, number> = { FREE: 0, PRO: 1, BUSINESS: 2 };
    return rank[target as PlanId] > rank[current];
  }

  usagePercent(used: number, limit: number | null, unlimited: boolean): number {
    if (unlimited || limit == null || limit <= 0) {
      return 0;
    }
    return Math.min(100, (used / limit) * 100);
  }

  isAtLimit(meter: UsageMeter): boolean {
    if (meter.unlimited || meter.limit == null) {
      return false;
    }
    return meter.used >= meter.limit;
  }

  formatUsageCompact(meter: UsageMeter): string {
    if (meter.unlimited || meter.limit == null) {
      return `${meter.used} / ∞`;
    }
    return `${meter.used} / ${meter.limit}`;
  }

  limitHeadline(e: EntitlementResponse): string {
    if (e.plan !== 'FREE' && e.cancelAtPeriodEnd && e.billingPeriodEnd) {
      return `Cancels on ${this.formatInstant(e.billingPeriodEnd)}. Your data stays available.`;
    }
    if (e.plan !== 'FREE' && e.status === 'PAST_DUE') {
      return 'Billing needs attention — your workspace data is safe.';
    }
    if (e.plan !== 'FREE' && e.billingPeriodEnd) {
      return `Renews ${this.formatInstant(e.billingPeriodEnd)}.`;
    }
    const limits = [
      { key: 'customer', meter: e.limits.activeCustomers },
      { key: 'quotation', meter: e.limits.quotationsThisMonth },
      { key: 'invoice', meter: e.limits.invoicesThisMonth },
    ] as const;
    const full = limits.filter((l) => this.isAtLimit(l.meter));
    if (full.length === 0) {
      if (e.plan === 'FREE') {
        return 'Room left this period — upgrade when you need more capacity.';
      }
      return 'You’re on unlimited core usage for this plan.';
    }
    if (full.length === 1) {
      return `Your ${full[0].key} limit is full.`;
    }
    return 'One or more Free limits are full this period.';
  }

  canUpgrade(planId: PlanId): boolean {
    const e = this.entitlements();
    if (!e || !this.isOwner) {
      return false;
    }
    if (!e.billingCheckoutAvailable) {
      return false;
    }
    if (!this.isUpgradeTarget(planId, e.plan)) {
      return false;
    }
    const item = this.catalog().find((c) => c.id === planId);
    return !!item?.billingAvailable;
  }

  annualSavingsLabel(planId: PlanId): string | null {
    if (planId !== 'PRO' || this.selectedInterval() !== 'ANNUAL') {
      return null;
    }
    return `Save ₹${PLAN_DISPLAY.PRO.annualSave}/year`;
  }

  upgrade(planId: PlanId): void {
    if (this.checkoutBusy() || !this.canUpgrade(planId)) {
      return;
    }
    this.checkoutBusy.set(true);
    this.checkoutMessage.set(null);
    const interval = this.selectedInterval();
    this.billingApi.createCheckout(planId, interval).subscribe({
      next: (checkout) => {
        this.razorpay
          .open({
            keyId: checkout.keyId,
            subscriptionId: checkout.subscriptionId,
            description: `QuoteFlow ${planId} (${interval.toLowerCase()})`,
          })
          .then((result) => {
            if (result === 'dismissed') {
              this.checkoutBusy.set(false);
              this.checkoutMessage.set('Checkout closed — no payment was completed.');
              return;
            }
            this.checkoutMessage.set('Verifying payment…');
            this.billingApi
              .verifyCheckout({
                razorpayPaymentId: result.razorpay_payment_id,
                razorpaySubscriptionId: result.razorpay_subscription_id,
                razorpaySignature: result.razorpay_signature,
              })
              .subscribe({
                next: (verified) => {
                  if (verified.activated) {
                    this.checkoutMessage.set('Payment received. Your plan is active.');
                    this.reload();
                    this.checkoutBusy.set(false);
                  } else {
                    this.checkoutMessage.set(
                      'Payment received. Activating your plan…',
                    );
                    this.pollActivation(0);
                  }
                },
                error: () => {
                  this.checkoutBusy.set(false);
                  this.checkoutMessage.set(
                    'We could not verify payment yet. If you were charged, refresh shortly or contact support.',
                  );
                },
              });
          })
          .catch(() => {
            this.checkoutBusy.set(false);
            this.checkoutMessage.set('Unable to open checkout. Try again.');
          });
      },
      error: (err) => {
        this.checkoutBusy.set(false);
        const code = err?.error?.code as string | undefined;
        if (code === 'BILLING_NOT_AVAILABLE' || code === 'BILLING_CONFIGURATION_ERROR') {
          this.checkoutMessage.set('Upgrade unavailable — billing is not enabled yet.');
        } else if (code === 'BILLING_ALREADY_ACTIVE') {
          this.checkoutMessage.set('You already have an active paid plan.');
          this.reload();
        } else {
          this.checkoutMessage.set('Could not start checkout. Try again.');
        }
      },
    });
  }

  cancelPaidPlan(): void {
    if (!this.isOwner || this.cancelBusy()) {
      return;
    }
    const end = this.entitlements()?.billingPeriodEnd;
    const ok = window.confirm(
      end
        ? `Your paid plan will remain available until ${this.formatInstant(end)}. After that, your workspace moves to Free limits. Your existing data will remain available. Continue?`
        : 'Cancel at period end? Your existing data will remain available. Continue?',
    );
    if (!ok) {
      return;
    }
    this.cancelBusy.set(true);
    this.billingApi.cancelSubscription().subscribe({
      next: () => {
        this.cancelBusy.set(false);
        this.checkoutMessage.set('Cancellation scheduled at period end.');
        this.reload();
      },
      error: () => {
        this.cancelBusy.set(false);
        this.checkoutMessage.set('Could not cancel subscription. Try again.');
      },
    });
  }

  private pollActivation(attempt: number): void {
    if (attempt >= 5) {
      this.checkoutBusy.set(false);
      this.checkoutMessage.set(
        'Payment received. Activation may take a moment — refresh Plan & usage shortly.',
      );
      this.reload();
      return;
    }
    window.setTimeout(() => {
      this.store.refresh().subscribe((ent) => {
        this.entitlements.set(ent);
        if (ent && ent.plan !== 'FREE') {
          this.checkoutBusy.set(false);
          this.checkoutMessage.set('Your paid plan is active.');
          this.reload();
        } else {
          this.pollActivation(attempt + 1);
        }
      });
    }, 1500);
  }

  private formatInstant(value: string): string {
    try {
      return new Date(value).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return value;
    }
  }
}
