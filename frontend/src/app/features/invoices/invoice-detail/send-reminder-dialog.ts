import { CurrencyPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ReminderTone } from '../../notifications/notification.models';

export interface SendReminderDialogData {
  invoiceNumber: string;
  recipientEmail: string | null | undefined;
  balanceDue: number | string;
  currency: string;
  emailSendingEnabled: boolean;
  mode: 'send' | 'resend' | 'retry';
}

export interface SendReminderDialogResult {
  tone: ReminderTone;
  message: string;
  resend: boolean;
}

@Component({
  selector: 'app-send-reminder-dialog',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.mode === 'resend' ? 'Resend payment reminder' : data.mode === 'retry' ? 'Retry payment reminder' : 'Send payment reminder' }}</h2>
    <mat-dialog-content>
      @if (!data.emailSendingEnabled) {
        <p role="alert">Email sending is available on Pro.</p>
      } @else if (!data.recipientEmail) {
        <p role="alert">Add an email address to this customer first.</p>
      } @else {
        <p>
          Remind <strong>{{ data.recipientEmail }}</strong> about
          <strong>{{ data.invoiceNumber }}</strong>
          (outstanding {{ data.balanceDue | currency: data.currency:'symbol-narrow':'1.2-2' }}).
        </p>
        <form [formGroup]="form">
          <mat-form-field appearance="outline" class="full">
            <mat-label>Tone</mat-label>
            <mat-select formControlName="tone">
              <mat-option value="FRIENDLY">Friendly</mat-option>
              <mat-option value="STANDARD">Standard</mat-option>
              <mat-option value="FIRM">Firm</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Optional note</mat-label>
            <textarea matInput rows="3" formControlName="message" maxlength="500"></textarea>
          </mat-form-field>
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancel</button>
      @if (data.emailSendingEnabled && data.recipientEmail) {
        <button mat-flat-button color="primary" type="button" (click)="submit()">
          {{ data.mode === 'resend' ? 'Resend reminder' : data.mode === 'retry' ? 'Retry send' : 'Send reminder' }}
        </button>
      } @else if (!data.emailSendingEnabled) {
        <a mat-stroked-button href="/app/plan">View plans</a>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; display: block; margin-top: 0.75rem; }
    mat-dialog-content { min-width: min(22rem, 86vw); }
    mat-dialog-actions button,
    mat-dialog-actions a {
      min-height: 2.75rem;
      min-width: 2.75rem;
    }
  `,
})
export class SendReminderDialog {
  readonly data = inject<SendReminderDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<SendReminderDialog, SendReminderDialogResult | null>);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    tone: this.fb.nonNullable.control<ReminderTone>('STANDARD'),
    message: ['', [Validators.maxLength(500)]],
  });

  submit(): void {
    if (!this.data.emailSendingEnabled || !this.data.recipientEmail || this.form.invalid) {
      return;
    }
    this.dialogRef.close({
      tone: this.form.controls.tone.value,
      message: this.form.controls.message.value.trim(),
      resend: this.data.mode === 'resend',
    });
  }
}
