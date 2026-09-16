import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { CustomerApiService } from '../../customers/customer-api.service';
import { CustomerSummary } from '../../customers/customer.models';
import { EntitlementStore } from '../../plan/entitlement.store';
import { QuoteAssistantApiService } from '../quote-assistant-api.service';
import {
  QuoteAssistantDialogComponent,
  QuoteAssistantDialogResult,
} from '../quote-assistant-dialog';
import { QuotationApiService } from '../quotation-api.service';
import {
  DiscountType,
  QuotationItemInput,
  previewTotals,
} from '../quotation.models';

@Component({
  selector: 'app-quotation-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CurrencyPipe,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './quotation-editor.html',
  styleUrl: './quotation-editor.scss',
})
export class QuotationEditorComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(QuotationApiService);
  private readonly customersApi = inject(CustomerApiService);
  private readonly quoteAssistantApi = inject(QuoteAssistantApiService);
  private readonly dialog = inject(MatDialog);
  private readonly entitlements = inject(EntitlementStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly quotationId = signal<string | null>(null);
  readonly version = signal<number | null>(null);
  readonly currency = signal('INR');
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly planLimitHint = signal<string | null>(null);
  readonly showViewPlans = signal(false);
  readonly customerOptions = signal<CustomerSummary[]>([]);
  readonly customerSearch = this.fb.nonNullable.control('');
  readonly aiAssistantAvailable = signal(false);

  readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    issueDate: ['', Validators.required],
    validUntil: [''],
    discountType: this.fb.nonNullable.control<DiscountType>('NONE'),
    discountValue: [0, [Validators.required, Validators.min(0)]],
    taxRate: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    notes: [''],
    terms: [''],
    items: this.fb.array([this.createItemGroup()]),
  });

  readonly preview = signal(previewTotals([{ description: '', quantity: 1, unitPrice: 0 }], 'NONE', 0, 0));

  get items(): FormArray {
    return this.form.controls.items;
  }

  get isEdit(): boolean {
    return this.quotationId() != null;
  }

  ngOnInit(): void {
    const today = new Date().toISOString().slice(0, 10);
    this.form.controls.issueDate.setValue(today);
    this.searchCustomers('');

    this.customerSearch.valueChanges
      .pipe(debounceTime(250), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => this.searchCustomers(q));

    this.form.valueChanges.pipe(debounceTime(100), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.preview.set(this.computePreview());
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.quotationId.set(id);
      this.load(id);
    } else {
      this.preview.set(this.computePreview());
      this.quoteAssistantApi.capabilities().subscribe({
        next: (caps) => this.aiAssistantAvailable.set(!!caps.quoteAssistant),
        error: () => this.aiAssistantAvailable.set(false),
      });
      this.entitlements.refresh().subscribe((e) => {
        if (e && !this.entitlements.canCreateQuotation()) {
          this.planLimitHint.set(
            `You've reached the ${e.plan} plan limit of ${e.limits.quotationsThisMonth.limit} quotations this month.`,
          );
          this.showViewPlans.set(true);
        }
      });
    }
  }

  openQuoteAssistant(): void {
    if (this.isEdit || !this.aiAssistantAvailable()) {
      return;
    }
    const ref = this.dialog.open(QuoteAssistantDialogComponent, {
      width: 'min(40rem, 96vw)',
      maxWidth: '96vw',
      autoFocus: 'first-tabbable',
      data: { currency: this.currency() },
    });
    ref.afterClosed().subscribe((result: QuoteAssistantDialogResult | undefined) => {
      if (!result) {
        return;
      }
      this.applyAssistantDraft(result);
    });
  }

  private applyAssistantDraft(result: QuoteAssistantDialogResult): void {
    if (result.customerId) {
      this.form.controls.customerId.setValue(result.customerId);
      this.searchCustomers(result.proposedCustomerName ?? '');
    } else if (result.proposedCustomerName) {
      this.customerSearch.setValue(result.proposedCustomerName);
      this.searchCustomers(result.proposedCustomerName);
      this.errorMessage.set(
        `No customer selected for “${result.proposedCustomerName}”. Choose or create a customer before saving.`,
      );
    }
    this.form.controls.discountType.setValue(result.discountType);
    this.form.controls.discountValue.setValue(result.discountValue);
    this.form.controls.taxRate.setValue(result.taxRate);
    if (result.notes) {
      this.form.controls.notes.setValue(result.notes);
    }
    while (this.items.length) {
      this.items.removeAt(0);
    }
    for (const item of result.items) {
      const group = this.createItemGroup();
      group.patchValue({
        description: item.description,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      });
      this.items.push(group);
    }
    this.preview.set(this.computePreview());
  }

  addItem(): void {
    if (this.items.length >= 100) {
      this.errorMessage.set('Maximum 100 line items allowed.');
      return;
    }
    this.items.push(this.createItemGroup());
  }

  removeItem(index: number): void {
    if (this.items.length <= 1) {
      return;
    }
    this.items.removeAt(index);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const items: QuotationItemInput[] = raw.items.map((item) => ({
      description: item.description.trim(),
      quantity: Number(item.quantity),
      unitPrice: Number(item.unitPrice),
    }));
    const payload = {
      customerId: raw.customerId,
      issueDate: raw.issueDate,
      validUntil: raw.validUntil || null,
      discountType: raw.discountType,
      discountValue: Number(raw.discountValue) || 0,
      taxRate: Number(raw.taxRate) || 0,
      notes: raw.notes.trim() || null,
      terms: raw.terms.trim() || null,
      items,
      version: this.version() ?? undefined,
    };

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.showViewPlans.set(false);
    const req$ = this.isEdit
      ? this.api.update(this.quotationId()!, payload)
      : this.api.create({ ...payload, currency: this.currency() });

    req$.subscribe({
      next: (quotation) => {
        this.submitting.set(false);
        this.entitlements.refresh().subscribe();
        void this.router.navigateByUrl(`/app/quotations/${quotation.id}`);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const planMsg = this.entitlements.messageFromApiError(err);
        if (planMsg) {
          this.errorMessage.set(planMsg);
          this.showViewPlans.set(true);
          this.entitlements.refresh().subscribe();
          return;
        }
        this.errorMessage.set(this.toSafeError(err));
      },
    });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (quotation) => {
        if (quotation.status !== 'DRAFT') {
          this.loadError.set('Only DRAFT quotations can be edited.');
          this.loading.set(false);
          return;
        }
        this.version.set(quotation.version);
        this.currency.set(quotation.currency);
        this.customerOptions.set([
          {
            id: quotation.customerId,
            displayName: quotation.customerDisplayName,
            companyName: quotation.customerCompanyName,
            email: quotation.customerEmail,
            phone: quotation.customerPhone,
            status: 'ACTIVE',
            updatedAt: quotation.updatedAt,
          },
        ]);
        while (this.items.length) {
          this.items.removeAt(0);
        }
        for (const item of quotation.items) {
          this.items.push(
            this.fb.nonNullable.group({
              description: [item.description, [Validators.required, Validators.maxLength(500)]],
              quantity: [item.quantity, [Validators.required, Validators.min(0.0001)]],
              unitPrice: [item.unitPrice, [Validators.required, Validators.min(0)]],
            }),
          );
        }
        this.form.patchValue({
          customerId: quotation.customerId,
          issueDate: quotation.issueDate,
          validUntil: quotation.validUntil ?? '',
          discountType: quotation.discountType,
          discountValue: Number(quotation.discountValue),
          taxRate: Number(quotation.taxRate),
          notes: quotation.notes ?? '',
          terms: quotation.terms ?? '',
        });
        this.preview.set({
          subtotal: Number(quotation.subtotal),
          discountAmount: Number(quotation.discountAmount),
          taxAmount: Number(quotation.taxAmount),
          totalAmount: Number(quotation.totalAmount),
        });
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.loadError.set(this.toSafeError(err));
      },
    });
  }

  private searchCustomers(q: string): void {
    this.customersApi.list({ q, status: 'ACTIVE', page: 0, size: 20 }).subscribe({
      next: (page) => this.customerOptions.set(page.content),
      error: () => this.customerOptions.set([]),
    });
  }

  private createItemGroup() {
    return this.fb.nonNullable.group({
      description: ['', [Validators.required, Validators.maxLength(500)]],
      quantity: [1, [Validators.required, Validators.min(0.0001)]],
      unitPrice: [0, [Validators.required, Validators.min(0)]],
    });
  }

  private computePreview() {
    const raw = this.form.getRawValue();
    const items = raw.items.map((item) => ({
      description: item.description,
      quantity: Number(item.quantity) || 0,
      unitPrice: Number(item.unitPrice) || 0,
    }));
    return previewTotals(items, raw.discountType, Number(raw.discountValue) || 0, Number(raw.taxRate) || 0);
  }

  private toSafeError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 409) {
        return err.error?.message || 'This quotation changed elsewhere. Reload and try again.';
      }
      if (err.status === 404) {
        return 'Customer or quotation not found.';
      }
      if (err.status === 400) {
        return err.error?.message || 'Please correct the form and try again.';
      }
    }
    return 'Unable to save quotation. Please try again.';
  }
}
