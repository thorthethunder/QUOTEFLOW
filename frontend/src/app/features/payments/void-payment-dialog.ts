import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PaymentApiService } from './payment-api.service';
import { Payment, paymentMethodLabel } from './payment.models';

export interface VoidPaymentDialogData {
  payment: Payment;
}

@Component({
  selector: 'app-void-payment-dialog',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>Void payment?</h2>
    <mat-dialog-content>
      <p>
        Void receipt <strong>{{ data.payment.receiptNumber }}</strong>
        ({{ data.payment.amount | currency: data.payment.currency }},
        {{ methodLabel }},
        {{ data.payment.paymentDate | date: 'mediumDate' }})?
        This is a correction — the payment is not deleted.
      </p>
      @if (errorMessage(); as err) {
        <p class="error" role="alert">{{ err }}</p>
      }
      <form [formGroup]="form" id="void-payment-form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="full">
          <mat-label>Reason (optional)</mat-label>
          <textarea matInput formControlName="reason" rows="3" maxlength="500"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="submitting()">Cancel</button>
      <button
        mat-flat-button
        color="warn"
        type="submit"
        form="void-payment-form"
        [disabled]="submitting()"
      >
        {{ submitting() ? 'Voiding…' : 'Void payment' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .error { margin: 0 0 1rem; color: var(--mat-sys-error); }
    .full { width: 100%; min-width: min(18rem, 72vw); }
  `,
})
export class VoidPaymentDialog {
  readonly data = inject<VoidPaymentDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<VoidPaymentDialog, boolean>);
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PaymentApiService);

  readonly methodLabel = paymentMethodLabel(this.data.payment.paymentMethod);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    reason: [''],
  });

  submit(): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const reason = this.form.controls.reason.value.trim();
    this.api.voidPayment(this.data.payment.id, { reason: reason || null }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.ref.close(true);
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('Unable to void payment. Please try again.');
      },
    });
  }
}
