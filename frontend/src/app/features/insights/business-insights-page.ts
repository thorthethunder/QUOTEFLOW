import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { BusinessInsightsApiService } from './business-insights-api.service';
import {
  CurrencyInsightGroup,
  InsightFact,
  InsightReference,
  ReportingInsightResponse,
} from './business-insights.models';

@Component({
  selector: 'app-business-insights-page',
  imports: [
    CurrencyPipe,
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './business-insights-page.html',
  styleUrl: './business-insights-page.scss',
})
export class BusinessInsightsPageComponent {
  private readonly api = inject(BusinessInsightsApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly analyze$ = new Subject<string>();

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly insight = signal<ReportingInsightResponse | null>(null);

  readonly form = this.fb.nonNullable.group({
    question: ['Summarize my business performance this month.', [Validators.required, Validators.maxLength(1000)]],
    period: ['THIS_MONTH'],
  });

  readonly suggestions = [
    'Summarize my business performance this month.',
    'Compare this month with last month.',
    'Why is my outstanding amount higher this month?',
    'Which invoices need my attention?',
    'Which customers account for most outstanding?',
  ];

  readonly currencyGroups = computed<CurrencyInsightGroup[]>(() => {
    const data = this.insight();
    if (!data) return [];
    const map = new Map<string, CurrencyInsightGroup>();
    for (const fact of data.facts) {
      const group = map.get(fact.currency) ?? { currency: fact.currency, currentAmounts: [] };
      if (fact.metric === 'COLLECTED') group.collected = fact;
      if (fact.metric === 'INVOICED') group.invoiced = fact;
      if (fact.metric === 'OUTSTANDING') group.outstanding = fact;
      map.set(fact.currency, group);
    }
    return Array.from(map.values()).sort((a, b) => a.currency.localeCompare(b.currency));
  });

  constructor() {
    this.analyze$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((question) =>
          this.api.analyze(question, this.form.getRawValue().period).pipe(
            catchError((err) => {
              this.error.set(this.messageFromError(err));
              this.loading.set(false);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((data) => {
        if (data) {
          this.insight.set(data);
        }
        this.loading.set(false);
      });

    this.analyze$.next(this.form.getRawValue().question);
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.analyze$.next(this.form.getRawValue().question.trim());
  }

  useSuggestion(question: string): void {
    this.form.patchValue({ question });
    this.analyze$.next(question);
  }

  retry(): void {
    this.analyze$.next(this.form.getRawValue().question.trim());
  }

  deltaLabel(fact: InsightFact | undefined): string {
    if (!fact) return 'No data';
    const amount = `${fact.currency} ${Math.abs(Number(fact.absoluteChange)).toFixed(2)}`;
    if (Number(fact.absoluteChange) > 0) return `Up ${amount}`;
    if (Number(fact.absoluteChange) < 0) return `Down ${amount}`;
    return `No change (${fact.currency})`;
  }

  percentLabel(fact: InsightFact | undefined): string {
    if (!fact) return '';
    if (fact.percentageChange === null) {
      return fact.comparisonReason === 'NO_PREVIOUS_BASE'
        ? 'No previous base'
        : 'No activity';
    }
    const prefix = fact.percentageChange > 0 ? '+' : '';
    return `${prefix}${Number(fact.percentageChange).toFixed(2)}%`;
  }

  trendClass(fact: InsightFact | undefined): string {
    if (!fact) return 'flat';
    if (Number(fact.absoluteChange) > 0) return 'positive';
    if (Number(fact.absoluteChange) < 0) return 'negative';
    return 'flat';
  }

  referenceRoute(ref: InsightReference): string[] | null {
    if (ref.type === 'INVOICE') return ['/app/invoices', ref.id];
    if (ref.type === 'CUSTOMER') return ['/app/customers', ref.id];
    if (ref.type === 'QUOTATION') return ['/app/quotations', ref.id];
    return null;
  }

  private messageFromError(err: unknown): string {
    const status = (err as { status?: number })?.status;
    if (status === 429) return 'Too many insight requests. Please wait and try again.';
    if (status === 503) return 'AI summary is unavailable, but reporting data is still available from Dashboard.';
    return 'Could not load business insights. Try again.';
  }
}
