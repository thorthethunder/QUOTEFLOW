import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { PlanApiService } from './plan-api.service';
import { EntitlementResponse, formatUsage, planLimitMessage } from './plan.models';

@Injectable({ providedIn: 'root' })
export class EntitlementStore {
  private readonly api = inject(PlanApiService);

  readonly entitlements = signal<EntitlementResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  refresh(): Observable<EntitlementResponse | null> {
    this.loading.set(true);
    this.error.set(null);
    return this.api.getEntitlements().pipe(
      tap((data) => {
        this.entitlements.set(data);
        this.loading.set(false);
      }),
      catchError(() => {
        this.error.set('Could not load plan usage.');
        this.loading.set(false);
        return of(null);
      }),
    );
  }

  canCreateCustomer(): boolean {
    const e = this.entitlements();
    if (!e) return true;
    const m = e.limits.activeCustomers;
    return m.unlimited || m.limit == null || m.used < m.limit;
  }

  canCreateQuotation(): boolean {
    const e = this.entitlements();
    if (!e) return true;
    const m = e.limits.quotationsThisMonth;
    return m.unlimited || m.limit == null || m.used < m.limit;
  }

  canCreateInvoice(): boolean {
    const e = this.entitlements();
    if (!e) return true;
    const m = e.limits.invoicesThisMonth;
    return m.unlimited || m.limit == null || m.used < m.limit;
  }

  canSendEmail(): boolean {
    return this.entitlements()?.features.emailSending === true;
  }

  messageFromApiError(err: unknown): string | null {
    const body = (err as { error?: { code?: string; details?: Record<string, unknown>; message?: string } })?.error;
    if (body?.code === 'FEATURE_NOT_AVAILABLE' && String(body.details?.['feature'] ?? '') === 'EMAIL_SENDING') {
      return 'Email sending is available on Pro.';
    }
    if (body?.code !== 'PLAN_LIMIT_REACHED') {
      return null;
    }
    return planLimitMessage({
      feature: String(body.details?.['feature'] ?? ''),
      used: Number(body.details?.['used'] ?? 0),
      limit: Number(body.details?.['limit'] ?? 0),
      plan: String(body.details?.['plan'] ?? ''),
    });
  }

  readonly formatUsage = formatUsage;
}
