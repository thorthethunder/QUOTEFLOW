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
import { InvoiceApiService } from '../invoice-api.service';
import {
  DiscountType,
  InvoiceItemInput,
  previewTotals,
} from '../invoice.models';

@Component({
  selector: 'app-invoice-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CurrencyPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './invoice-editor.html',
  styleUrl: './invoice-editor.scss',
})
export class InvoiceEditorComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(InvoiceApiService);
  private readonly customersApi = inject(CustomerApiService);
  private readonly entitlements = inject(EntitlementStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly invoiceId = signal<string | null>(null);
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

  readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    issueDate: ['', Validators.required],
    dueDate: [''],
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
    return this.invoiceId() != null;
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
      this.invoiceId.set(id);
      this.load(id);
    } else {
      this.preview.set(this.computePreview());
      this.entitlements.refresh().subscribe((e) => {
        if (e && !this.entitlements.canCreateInvoice()) {
          this.planLimitHint.set(
            `You've reached the ${e.plan} plan limit of ${e.limits.invoicesThisMonth.limit} invoices this month.`,
          );
          this.showViewPlans.set(true);
        }
      });
    }
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
    const items: InvoiceItemInput[] = raw.items.map((item) => ({
      description: item.description.trim(),
      quantity: Number(item.quantity),
      unitPrice: Number(item.unitPrice),
    }));
    const payload = {
      customerId: raw.customerId,
      issueDate: raw.issueDate,
      dueDate: raw.dueDate || null,
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
      ? this.api.update(this.invoiceId()!, payload)
      : this.api.create({ ...payload, currency: this.currency() });

    req$.subscribe({
      next: (invoice) => {
        this.submitting.set(false);
        this.entitlements.refresh().subscribe();
        void this.router.navigateByUrl(`/app/invoices/${invoice.id}`);
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
      next: (invoice) => {
        if (invoice.status !== 'DRAFT') {
          this.loadError.set('Only DRAFT invoices can be edited.');
          this.loading.set(false);
          return;
        }
        this.version.set(invoice.version);
        this.currency.set(invoice.currency);
        this.customerOptions.set([
          {
            id: invoice.customerId,
            displayName: invoice.customerDisplayName,
            companyName: invoice.customerCompanyName,
            email: invoice.customerEmail,
            phone: invoice.customerPhone,
            status: 'ACTIVE',
            updatedAt: invoice.updatedAt,
          },
        ]);
        while (this.items.length) {
          this.items.removeAt(0);
        }
        for (const item of invoice.items) {
          this.items.push(
            this.fb.nonNullable.group({
              description: [item.description, [Validators.required, Validators.maxLength(500)]],
              quantity: [item.quantity, [Validators.required, Validators.min(0.0001)]],
              unitPrice: [item.unitPrice, [Validators.required, Validators.min(0)]],
            }),
          );
        }
        this.form.patchValue({
          customerId: invoice.customerId,
          issueDate: invoice.issueDate,
          dueDate: invoice.dueDate ?? '',
          discountType: invoice.discountType,
          discountValue: Number(invoice.discountValue),
          taxRate: Number(invoice.taxRate),
          notes: invoice.notes ?? '',
          terms: invoice.terms ?? '',
        });
        this.preview.set({
          subtotal: Number(invoice.subtotal),
          discountAmount: Number(invoice.discountAmount),
          taxAmount: Number(invoice.taxAmount),
          totalAmount: Number(invoice.totalAmount),
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
        return err.error?.message || 'This invoice changed elsewhere. Reload and try again.';
      }
      if (err.status === 404) {
        return 'Customer or invoice not found.';
      }
      if (err.status === 400) {
        return err.error?.message || 'Please correct the form and try again.';
      }
    }
    return 'Unable to save invoice. Please try again.';
  }
}
