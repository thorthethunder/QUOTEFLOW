import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
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
import { catchError, forkJoin, of } from 'rxjs';
import { AiWorkflow, WorkflowStep } from './ai-workflows.models';
import { AiWorkflowsApiService } from './ai-workflows-api.service';
import { ActionProposalDetail } from '../copilot/business-copilot-api.service';

type ProposalView = {
  step: WorkflowStep;
  detail: ActionProposalDetail | null;
  loading: boolean;
  busy: boolean;
  error: string | null;
};

@Component({
  selector: 'app-ai-workflows-page',
  imports: [
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
  templateUrl: './ai-workflows-page.html',
  styleUrl: './ai-workflows-page.scss',
})
export class AiWorkflowsPageComponent {
  private readonly api = inject(AiWorkflowsApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly workflow = signal<AiWorkflow | null>(null);
  readonly proposals = signal<ProposalView[]>([]);

  readonly form = this.fb.nonNullable.group({
    goal: ['Prepare reminders for my 3 largest unpaid invoices.', [Validators.required, Validators.maxLength(1000)]],
    maxItems: [3, [Validators.required, Validators.min(1), Validators.max(3)]],
  });

  readonly waitingSteps = computed(() =>
    this.workflow()?.steps.filter((s) => s.status === 'WAITING_FOR_APPROVAL' && s.actionProposalId) ?? [],
  );

  constructor() {
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => {
          const latest = items[0];
          if (!latest) {
            this.loading.set(false);
            return;
          }
          this.loadWorkflow(latest.id);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(this.mapError(err));
        },
      });
  }

  start(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.api
      .startPaymentFollowUp({
        goal: value.goal.trim(),
        maxItems: value.maxItems,
        idempotencyKey: crypto.randomUUID(),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (workflow) => {
          this.busy.set(false);
          this.setWorkflow(workflow);
        },
        error: (err: HttpErrorResponse) => {
          this.busy.set(false);
          this.error.set(this.mapError(err));
        },
      });
  }

  resume(): void {
    const workflow = this.workflow();
    if (!workflow || this.busy()) return;
    this.busy.set(true);
    this.api
      .resume(workflow.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.setWorkflow(updated);
        },
        error: (err: HttpErrorResponse) => {
          this.busy.set(false);
          this.error.set(this.mapError(err));
        },
      });
  }

  cancelWorkflow(): void {
    const workflow = this.workflow();
    if (!workflow || this.busy()) return;
    this.busy.set(true);
    this.api
      .cancel(workflow.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.setWorkflow(updated);
        },
        error: (err: HttpErrorResponse) => {
          this.busy.set(false);
          this.error.set(this.mapError(err));
        },
      });
  }

  approve(view: ProposalView): void {
    if (!view.step.actionProposalId || view.busy) return;
    this.updateProposal(view.step.actionProposalId, { busy: true, error: null });
    this.api.confirmProposal(view.step.actionProposalId).subscribe({
      next: () => this.resume(),
      error: (err: HttpErrorResponse) =>
        this.updateProposal(view.step.actionProposalId!, { busy: false, error: this.mapError(err) }),
    });
  }

  cancelProposal(view: ProposalView): void {
    if (!view.step.actionProposalId || view.busy) return;
    this.updateProposal(view.step.actionProposalId, { busy: true, error: null });
    this.api.cancelProposal(view.step.actionProposalId).subscribe({
      next: () => this.resume(),
      error: (err: HttpErrorResponse) =>
        this.updateProposal(view.step.actionProposalId!, { busy: false, error: this.mapError(err) }),
    });
  }

  invoiceHref(detail: ActionProposalDetail | null): string[] | null {
    const invoiceId = detail?.payload?.['invoiceId'];
    return typeof invoiceId === 'string' ? ['/app/invoices', invoiceId] : null;
  }

  private loadWorkflow(id: string): void {
    this.api
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (workflow) => {
          this.loading.set(false);
          this.setWorkflow(workflow);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(this.mapError(err));
        },
      });
  }

  private setWorkflow(workflow: AiWorkflow): void {
    this.workflow.set(workflow);
    this.loadProposals(workflow);
  }

  private loadProposals(workflow: AiWorkflow): void {
    const steps = workflow.steps.filter((s) => s.actionProposalId);
    if (steps.length === 0) {
      this.proposals.set([]);
      return;
    }
    this.proposals.set(steps.map((step) => ({ step, detail: null, loading: true, busy: false, error: null })));
    forkJoin(
      steps.map((step) =>
        this.api.getProposal(step.actionProposalId!).pipe(
          catchError((err: HttpErrorResponse) =>
            of({
              error: this.mapError(err),
              proposalId: step.actionProposalId,
            } as unknown as ActionProposalDetail & { error?: string }),
          ),
        ),
      ),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((details) => {
        this.proposals.set(
          steps.map((step, index) => {
            const detail = details[index] as ActionProposalDetail & { error?: string };
            return {
              step,
              detail: detail.error ? null : detail,
              loading: false,
              busy: false,
              error: detail.error ?? null,
            };
          }),
        );
      });
  }

  private updateProposal(proposalId: string, patch: Partial<ProposalView>): void {
    this.proposals.update((views) =>
      views.map((view) => (view.step.actionProposalId === proposalId ? { ...view, ...patch } : view)),
    );
  }

  private mapError(err: HttpErrorResponse): string {
    const code = err.error?.code as string | undefined;
    if (code === 'AI_WORKFLOWS_DISABLED') return 'AI workflows are disabled.';
    if (code === 'AI_ACTIONS_DISABLED') return 'AI-assisted actions are disabled.';
    if (code === 'FEATURE_NOT_AVAILABLE') return 'Email sending is not available on the current plan.';
    if (code === 'INVOICE_NOT_OUTSTANDING') return 'The invoice is no longer outstanding. No reminder was queued.';
    if (code === 'AI_ACTION_STALE_BALANCE') return 'The balance changed after review. Prepare a new reminder.';
    if (code === 'AI_ACTION_STALE_RECIPIENT') return 'The recipient changed after review. Prepare a new reminder.';
    if (code === 'RATE_LIMITED') return 'Too many workflow requests. Please wait and try again.';
    if (code === 'WORKFLOW_LIMIT_EXCEEDED' || code === 'VALIDATION_ERROR') return 'Choose between 1 and 3 reminders.';
    return 'Could not complete the workflow request.';
  }
}
