import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { PaymentApiService } from './payment-api.service';
import { PAYMENT_METHODS, PaymentMethod } from './payment.models';

export interface RecordPaymentDialogData {
  invoiceId: string;
  currency: string;
  balanceDue: number;
}

@Component({
  selector: 'app-record-payment-dialog',
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
    <h2 mat-dialog-title>Record payment</h2>
    <mat-dialog-content>
      <p class="balance">
        Balance due:
        <strong>{{ data.balanceDue | currency: data.currency }}</strong>
      </p>
      @if (errorMessage(); as err) {
        <p class="error" role="alert">{{ err }}</p>
      }
      <form [formGroup]="form" class="form" id="record-payment-form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Amount</mat-label>
          <input matInput type="number" formControlName="amount" min="0.01" step="0.01" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Payment date</mat-label>
          <input matInput type="date" formControlName="paymentDate" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Method</mat-label>
          <mat-select formControlName="paymentMethod">
            @for (method of methods; track method.value) {
              <mat-option [value]="method.value">{{ method.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Reference</mat-label>
          <input matInput formControlName="reference" maxlength="200" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Notes</mat-label>
          <textarea matInput formControlName="notes" rows="3" maxlength="2000"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="submitting()">Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="submit"
        form="record-payment-form"
        [disabled]="form.invalid || submitting()"
      >
        {{ submitting() ? 'Recording…' : 'Record payment' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .balance { margin: 0 0 1rem; color: var(--qf-text-muted); }
    .error { margin: 0 0 1rem; color: var(--mat-sys-error); }
    .form { display: flex; flex-direction: column; gap: 0.75rem; min-width: min(20rem, 72vw); }
    mat-form-field { width: 100%; }
  `,
})
export class RecordPaymentDialog {
  readonly data = inject<RecordPaymentDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RecordPaymentDialog, boolean>);
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PaymentApiService);

  readonly methods = PAYMENT_METHODS;
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    amount: [this.data.balanceDue, [Validators.required, Validators.min(0.01)]],
    paymentDate: [new Date().toISOString().slice(0, 10), Validators.required],
    paymentMethod: this.fb.nonNullable.control<PaymentMethod>('CASH', Validators.required),
    reference: [''],
    notes: [''],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const value = this.form.getRawValue();
    this.api
      .create(this.data.invoiceId, {
        amount: value.amount,
        paymentDate: value.paymentDate || null,
        paymentMethod: value.paymentMethod,
        reference: value.reference.trim() || null,
        notes: value.notes.trim() || null,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.ref.close(true);
        },
        error: () => {
          this.submitting.set(false);
          this.errorMessage.set('Unable to record payment. Please try again.');
        },
      });
  }
}
