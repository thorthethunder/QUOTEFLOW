import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  ActionConfirmResponse,
  ActionProposalDetail,
  ActionProposalSummary,
  BusinessCopilotApiService,
} from './business-copilot-api.service';

@Component({
  selector: 'app-action-approval-panel',
  imports: [DatePipe, MatButtonModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './action-approval-panel.html',
  styleUrl: './action-approval-panel.scss',
})
export class ActionApprovalPanelComponent implements OnInit {
  private readonly api = inject(BusinessCopilotApiService);

  readonly summary = input.required<ActionProposalSummary>();
  readonly closed = output<void>();
  readonly confirmed = output<ActionConfirmResponse>();

  readonly detail = signal<ActionProposalDetail | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<ActionConfirmResponse | null>(null);

  ngOnInit(): void {
    this.api.getProposal(this.summary().proposalId).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.mapError(err));
      },
    });
  }

  confirm(): void {
    if (this.busy() || this.success()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.confirmProposal(this.summary().proposalId).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.success.set(res);
        this.confirmed.emit(res);
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(this.mapError(err));
      },
    });
  }

  cancel(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.cancelProposal(this.summary().proposalId).subscribe({
      next: () => {
        this.busy.set(false);
        this.closed.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(this.mapError(err));
      },
    });
  }

  resultHref(res: ActionConfirmResponse): string | null {
    if (!res.resultReferenceId || !res.resultReferenceType) {
      return null;
    }
    if (res.resultReferenceType === 'QUOTATION') {
      return `/app/quotations/${res.resultReferenceId}`;
    }
    if (res.resultReferenceType === 'INVOICE') {
      return `/app/invoices/${res.resultReferenceId}`;
    }
    return null;
  }

  lineItems(payload: Record<string, unknown> | undefined): unknown[] {
    const items = payload?.['items'];
    return Array.isArray(items) ? items : [];
  }

  private mapError(err: HttpErrorResponse): string {
    const code = err.error?.code as string | undefined;
    if (code === 'INVOICE_NOT_OUTSTANDING') {
      return 'The invoice has been paid since this reminder was prepared. No reminder was sent.';
    }
    if (code === 'AI_ACTION_STALE_BALANCE') {
      return 'The outstanding balance changed after this reminder was prepared. Please review a new reminder before sending.';
    }
    if (code === 'AI_ACTION_STALE_RECIPIENT') {
      return 'The recipient email changed after this reminder was prepared. Please review a new reminder before sending.';
    }
    if (code === 'AI_ACTION_EXPIRED' || code === 'AI_ACTION_REVALIDATION' || code === 'AI_ACTION_INTEGRITY') {
      return 'This action can no longer be completed with the reviewed details. Please prepare it again.';
    }
    if (code === 'AI_ACTION_ALREADY_EXECUTED' || code === 'AI_ACTION_CONFLICT') {
      return 'This action was already confirmed.';
    }
    if (code === 'AI_ACTION_CANCELLED') {
      return 'This action was cancelled.';
    }
    if (code === 'AI_ACTIONS_DISABLED') {
      return 'AI-assisted actions are disabled.';
    }
    if (code === 'FEATURE_NOT_AVAILABLE') {
      return 'Email sending is not available on the current plan.';
    }
    return 'Could not complete this approval. Please try again or use the normal QuoteFlow screens.';
  }
}
