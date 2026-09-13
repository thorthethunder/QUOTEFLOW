import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export type SendEmailMode = 'send' | 'resend' | 'retry';

export interface SendQuotationEmailDialogData {
  quotationNumber: string;
  recipientEmail: string | null | undefined;
  emailSendingEnabled: boolean;
  mode: SendEmailMode;
}

export interface SendQuotationEmailDialogResult {
  message: string;
  resend: boolean;
}

@Component({
  selector: 'app-send-quotation-email-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title id="send-email-title">{{ title }}</h2>
    <mat-dialog-content>
      @if (!data.emailSendingEnabled) {
        <p role="alert">Email sending is available on Pro.</p>
      } @else if (!data.recipientEmail) {
        <p role="alert">Add an email address to this customer first.</p>
      } @else {
        <p>
          {{ intro }}
          <strong class="wrap">{{ data.quotationNumber }}</strong>
          to
          <strong class="wrap">{{ data.recipientEmail }}</strong>.
        </p>
        <form [formGroup]="form">
          <mat-form-field appearance="outline" class="full">
            <mat-label>Optional message</mat-label>
            <textarea
              matInput
              rows="3"
              formControlName="message"
              maxlength="500"
              aria-describedby="message-hint"
            ></textarea>
            <mat-hint id="message-hint" align="end">{{ messageLength }}/500</mat-hint>
          </mat-form-field>
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancel</button>
      @if (data.emailSendingEnabled && data.recipientEmail) {
        <button
          mat-flat-button
          color="primary"
          type="button"
          (click)="submit()"
          [disabled]="form.invalid"
        >
          {{ confirmLabel }}
        </button>
      } @else if (!data.emailSendingEnabled) {
        <a mat-stroked-button href="/app/plan">View plans</a>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; margin-top: 0.75rem; }
    mat-dialog-content { min-width: min(22rem, 86vw); max-width: 94vw; }
    .wrap { overflow-wrap: anywhere; }
    mat-dialog-actions button { min-height: 2.75rem; }
  `,
})
export class SendQuotationEmailDialog {
  readonly data = inject<SendQuotationEmailDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<SendQuotationEmailDialog, SendQuotationEmailDialogResult | null>);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    message: ['', [Validators.maxLength(500)]],
  });

  get messageLength(): number {
    return this.form.controls.message.value.length;
  }

  get title(): string {
    if (this.data.mode === 'resend') return 'Resend quotation email';
    if (this.data.mode === 'retry') return 'Retry quotation email';
    return 'Send quotation email';
  }

  get intro(): string {
    if (this.data.mode === 'resend') return 'Send another email for';
    if (this.data.mode === 'retry') return 'Retry sending';
    return 'Send';
  }

  get confirmLabel(): string {
    if (this.data.mode === 'resend') return 'Resend email';
    if (this.data.mode === 'retry') return 'Retry send';
    return 'Send email';
  }

  submit(): void {
    if (!this.data.emailSendingEnabled || !this.data.recipientEmail || this.form.invalid) {
      return;
    }
    this.dialogRef.close({
      message: this.form.controls.message.value.trim(),
      resend: this.data.mode === 'resend',
    });
  }
}
