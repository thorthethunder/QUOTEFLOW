import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  QuoteAssistantApiService,
  QuoteAssistantProposal,
} from './quote-assistant-api.service';

export type QuoteAssistantDialogResult = {
  customerId: string | null;
  proposedCustomerName: string | null;
  notes: string | null;
  discountType: 'NONE' | 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  taxRate: number;
  items: { description: string; quantity: number; unitPrice: number }[];
};

@Component({
  selector: 'app-quote-assistant-dialog',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './quote-assistant-dialog.html',
  styleUrl: './quote-assistant-dialog.scss',
})
export class QuoteAssistantDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(QuoteAssistantApiService);
  private readonly dialogRef = inject(MatDialogRef<QuoteAssistantDialogComponent, QuoteAssistantDialogResult>);
  readonly data = inject<{ currency: string }>(MAT_DIALOG_DATA);

  readonly generating = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly proposal = signal<QuoteAssistantProposal | null>(null);

  readonly promptControl = this.fb.nonNullable.control('', [
    Validators.required,
    Validators.maxLength(4000),
  ]);

  generate(): void {
    if (this.promptControl.invalid || this.generating()) {
      this.promptControl.markAsTouched();
      return;
    }
    this.generating.set(true);
    this.errorMessage.set(null);
    this.proposal.set(null);
    this.api.draft(this.promptControl.value.trim()).subscribe({
      next: (proposal) => {
        this.proposal.set(proposal);
        this.generating.set(false);
      },
      error: (err: unknown) => {
        this.generating.set(false);
        this.errorMessage.set(this.mapError(err));
      },
    });
  }

  useDraft(): void {
    const p = this.proposal();
    if (!p) {
      return;
    }
    const items = p.items
      .filter((i) => i.description?.trim())
      .map((i) => ({
        description: i.description.trim(),
        quantity: i.quantity != null && i.quantity > 0 ? Number(i.quantity) : 1,
        unitPrice: i.unitPrice != null && i.unitPrice >= 0 ? Number(i.unitPrice) : 0,
      }));
    if (items.length === 0) {
      this.errorMessage.set('Add at least one line item before continuing.');
      return;
    }
    const discountType =
      p.discountType === 'PERCENTAGE' || p.discountType === 'FIXED' ? p.discountType : 'NONE';
    this.dialogRef.close({
      customerId: p.customer.matched ? p.customer.id : null,
      proposedCustomerName: p.proposedCustomerName,
      notes: p.notes,
      discountType,
      discountValue: Number(p.discountValue) || 0,
      taxRate: Number(p.taxRate) || 0,
      items,
    });
  }

  private mapError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const code = err.error?.code as string | undefined;
      if (code === 'AI_DISABLED') {
        return 'AI drafting is disabled. Create the quotation manually.';
      }
      if (code === 'AI_UNAVAILABLE' || err.status === 503) {
        return 'AI drafting is temporarily unavailable. You can continue creating the quotation manually.';
      }
      if (code === 'AI_RATE_LIMITED' || err.status === 429) {
        return 'Too many AI draft requests. Please wait and try again, or create manually.';
      }
      if (err.error?.message) {
        return String(err.error.message);
      }
    }
    return 'Could not generate a draft. Please try again or create the quotation manually.';
  }
}
