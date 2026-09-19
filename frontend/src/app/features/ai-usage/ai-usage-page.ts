import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AiUsageApiService } from './ai-usage-api.service';
import { AI_FEATURE_LABELS, AiUsageFeature } from './ai-usage.models';

@Component({
  selector: 'app-ai-usage-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './ai-usage-page.html',
  styleUrl: './ai-usage-page.scss',
})
export class AiUsagePageComponent {
  private readonly api = inject(AiUsageApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly features = signal<AiUsageFeature[]>([]);

  readonly period = computed(() => this.features()[0]?.period ?? '');
  readonly resetAt = computed(() => this.features()[0]?.resetAt ?? null);
  readonly enabledCount = computed(() => this.features().filter((f) => f.entitled).length);
  readonly limitedCount = computed(() =>
    this.features().filter((f) => f.entitled && f.limit > 0 && f.remaining === 0).length,
  );

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .summary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.features.set(response.features ?? []);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(this.mapError(err));
        },
      });
  }

  label(feature: string): string {
    return AI_FEATURE_LABELS[feature] ?? feature.replaceAll('_', ' ').toLowerCase();
  }

  percent(feature: AiUsageFeature): number {
    if (!feature.entitled || feature.limit <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((feature.used / feature.limit) * 100));
  }

  state(feature: AiUsageFeature): 'disabled' | 'full' | 'near' | 'ok' {
    if (!feature.entitled) {
      return 'disabled';
    }
    if (feature.limit > 0 && feature.remaining === 0) {
      return 'full';
    }
    if (feature.limit > 0 && this.percent(feature) >= 80) {
      return 'near';
    }
    return 'ok';
  }

  stateLabel(feature: AiUsageFeature): string {
    const state = this.state(feature);
    if (state === 'disabled') return 'Disabled on this plan';
    if (state === 'full') return 'Limit reached';
    if (state === 'near') return 'Near limit';
    return 'Available';
  }

  usageLabel(feature: AiUsageFeature): string {
    if (!feature.entitled) {
      return 'Not included';
    }
    return `${feature.used} of ${feature.limit}`;
  }

  private mapError(err: HttpErrorResponse): string {
    if (err.status === 401 || err.status === 403) {
      return 'You do not have access to AI usage for this workspace.';
    }
    return 'Could not load AI usage. Try again.';
  }
}
