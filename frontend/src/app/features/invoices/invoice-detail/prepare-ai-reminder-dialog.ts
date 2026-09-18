import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ActionProposalSummary,
  BusinessCopilotApiService,
} from '../../copilot/business-copilot-api.service';
import { ActionApprovalPanelComponent } from '../../copilot/action-approval-panel';

export interface PrepareAiReminderDialogData {
  invoiceId: string;
  invoiceNumber: string;
  recipientEmail: string | null | undefined;
  balanceDue: number | string;
  currency: string;
  customerDisplayName: string;
  aiActionsEnabled: boolean;
  emailSendingEnabled: boolean;
}

@Component({
  selector: 'app-prepare-ai-reminder-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ActionApprovalPanelComponent,
  ],
  template: `
    <h2 mat-dialog-title>Prepare payment reminder</h2>
    <mat-dialog-content>
      @if (!data.aiActionsEnabled) {
        <p role="alert">AI-assisted actions are disabled.</p>
      } @else if (!data.emailSendingEnabled) {
        <p role="alert">Email sending is available on Pro.</p>
      } @else if (!data.recipientEmail) {
        <p role="alert">No email address is available for this customer.</p>
      } @else if (proposal(); as p) {
        <app-action-approval-panel
          [summary]="p"
          (closed)="dialogRef.close(null)"
          (confirmed)="dialogRef.close($event)"
        />
      } @else {
        <p>
          Draft a reminder for <strong>{{ data.invoiceNumber }}</strong>
          ({{ data.currency }} {{ data.balanceDue }}) to
          <strong>{{ data.recipientEmail }}</strong>.
          You will review and approve before anything is queued.
        </p>
        <form [formGroup]="form">
          <mat-form-field appearance="outline" class="full">
            <mat-label>Subject</mat-label>
            <input matInput formControlName="subject" maxlength="200" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Message (plain text)</mat-label>
            <textarea matInput rows="5" formControlName="bodyPlainText" maxlength="2000"></textarea>
          </mat-form-field>
        </form>
        @if (error(); as err) {
          <p class="err" role="alert">{{ err }}</p>
        }
      }
    </mat-dialog-content>
    @if (!proposal()) {
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close [disabled]="busy()">Cancel</button>
        @if (data.aiActionsEnabled && data.emailSendingEnabled && data.recipientEmail) {
          <button mat-flat-button color="primary" type="button" [disabled]="busy() || form.invalid" (click)="prepare()">
            @if (busy()) {
              <mat-spinner diameter="18"></mat-spinner>
            }
            Prepare for review
          </button>
        }
      </mat-dialog-actions>
    }
  `,
  styles: `
    .full { width: 100%; display: block; margin-top: 0.75rem; }
    mat-dialog-content { min-width: min(24rem, 90vw); max-width: 40rem; }
    .err { color: var(--qf-danger, #b42318); }
    button mat-spinner { display: inline-block; margin-right: 0.35rem; }
  `,
})
export class PrepareAiReminderDialog {
  readonly data = inject<PrepareAiReminderDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<PrepareAiReminderDialog, unknown>);
  private readonly api = inject(BusinessCopilotApiService);
  private readonly fb = inject(FormBuilder);

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly proposal = signal<ActionProposalSummary | null>(null);

  readonly form = this.fb.nonNullable.group({
    subject: [
      `Payment reminder: ${this.data.invoiceNumber}`,
      [Validators.required, Validators.maxLength(200)],
    ],
    bodyPlainText: [
      `Hello ${this.data.customerDisplayName},\n\nThis is a polite reminder that invoice ${this.data.invoiceNumber} has an outstanding balance of ${this.data.currency} ${this.data.balanceDue}. Please arrange payment at your earliest convenience.\n\nThank you.`,
      [Validators.required, Validators.maxLength(2000)],
    ],
  });

  prepare(): void {
    if (this.busy() || this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.api
      .preparePaymentReminder({
        invoiceId: this.data.invoiceId,
        subject: value.subject,
        bodyPlainText: value.bodyPlainText,
      })
      .subscribe({
        next: (p) => {
          this.busy.set(false);
          this.proposal.set(p);
        },
        error: (err: HttpErrorResponse) => {
          this.busy.set(false);
          const code = err.error?.code as string | undefined;
          if (code === 'RECIPIENT_EMAIL_MISSING') {
            this.error.set('No email address is available for this customer.');
          } else if (code === 'INVOICE_NOT_OUTSTANDING') {
            this.error.set('This invoice has no outstanding balance.');
          } else if (code === 'FEATURE_NOT_AVAILABLE') {
            this.error.set('Email sending is not available on the current plan.');
          } else if (code === 'AI_ACTIONS_DISABLED') {
            this.error.set('AI-assisted actions are disabled.');
          } else if (code === 'UNSUPPORTED_REMINDER_CLAIM') {
            this.error.set(
              'Reminder text must stay factual and polite — no late fees, penalties, or legal threats.',
            );
          } else {
            this.error.set('Could not prepare the reminder. Please try again.');
          }
        },
      });
  }
}
