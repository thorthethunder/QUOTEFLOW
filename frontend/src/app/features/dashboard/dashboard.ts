import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { EntitlementStore } from '../plan/entitlement.store';
import { DashboardApiService } from './dashboard-api.service';
import {
  DashboardSummary,
  MoneyByCurrency,
  PeriodPreset,
  paymentStateLabel,
} from './dashboard.models';

@Component({
  selector: 'app-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent {
  private readonly api = inject(DashboardApiService);
  private readonly auth = inject(AuthService);
  private readonly entitlements = inject(EntitlementStore);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload$ = new Subject<{ from?: string; to?: string }>();

  readonly user = this.auth.currentUser;
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly summary = signal<DashboardSummary | null>(null);
  readonly preset = signal<PeriodPreset>('this_month');
  readonly paymentStateLabel = paymentStateLabel;
  readonly planLabel = signal<string | null>(null);

  readonly customForm = this.fb.nonNullable.group({
    from: '',
    to: '',
  });

  readonly seriesMax = computed(() => {
    const points = this.summary()?.collectionsSeries ?? [];
    return points.reduce((max, p) => Math.max(max, Number(p.amount) || 0), 0) || 1;
  });

  constructor() {
    this.reload$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap(({ from, to }) =>
          this.api.summary(from, to).pipe(
            catchError(() => {
              this.error.set('Could not load dashboard. Try again.');
              this.loading.set(false);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((data) => {
        if (data) {
          this.summary.set(data);
          this.customForm.patchValue({ from: data.from, to: data.to }, { emitEvent: false });
        }
        this.loading.set(false);
      });

    this.applyPreset('this_month');
    this.entitlements.refresh().subscribe((e) => {
      this.planLabel.set(e ? e.planDisplayName : null);
    });
  }

  applyPreset(preset: PeriodPreset): void {
    this.preset.set(preset);
    if (preset === 'custom') {
      return;
    }
    if (preset === 'this_month') {
      this.reload$.next({});
      return;
    }
    const today = new Date();
    const to = this.toIsoDate(today);
    if (preset === 'last_30') {
      const from = new Date(today);
      from.setDate(from.getDate() - 29);
      this.reload$.next({ from: this.toIsoDate(from), to });
      return;
    }
    // this_year
    const from = new Date(today.getFullYear(), 0, 1);
    this.reload$.next({ from: this.toIsoDate(from), to });
  }

  applyCustomRange(): void {
    const { from, to } = this.customForm.getRawValue();
    if (!from || !to) {
      this.error.set('Choose both from and to dates.');
      return;
    }
    if (from > to) {
      this.error.set('From date must be on or before to date.');
      return;
    }
    this.preset.set('custom');
    this.reload$.next({ from, to });
  }

  retry(): void {
    const { from, to } = this.customForm.getRawValue();
    if (this.preset() === 'this_month' && !from) {
      this.reload$.next({});
      return;
    }
    this.reload$.next({ from, to });
  }

  hasMoney(rows: MoneyByCurrency[] | undefined): boolean {
    return !!rows && rows.length > 0;
  }

  isQuietTenant(s: DashboardSummary): boolean {
    return (
      s.invoices.sentCount === 0 &&
      s.invoices.draftCount === 0 &&
      s.invoices.cancelledCount === 0 &&
      s.quotations.draftCount === 0 &&
      s.quotations.sentCount === 0 &&
      s.quotations.cancelledCount === 0 &&
      s.quotations.convertedCount === 0 &&
      s.payments.recordedCount === 0
    );
  }

  formatMoneyList(rows: MoneyByCurrency[] | undefined): string {
    if (!rows || rows.length === 0) {
      return 'No amount yet';
    }
    return rows.map((r) => `${r.currency} ${Number(r.amount).toFixed(2)}`).join(' · ');
  }

  barHeight(amount: number): number {
    return Math.max(4, Math.round((Number(amount) / this.seriesMax()) * 100));
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
