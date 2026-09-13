import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EntitlementStore } from '../../plan/entitlement.store';
import { CustomerApiService } from '../customer-api.service';
import { CustomerWritePayload } from '../customer.models';

@Component({
  selector: 'app-customer-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './customer-form.html',
  styleUrl: './customer-form.scss',
})
export class CustomerFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(CustomerApiService);
  private readonly entitlements = inject(EntitlementStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly customerId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly planLimitHint = signal<string | null>(null);
  readonly showViewPlans = signal(false);

  readonly form = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.email, Validators.maxLength(320)]],
    phone: ['', [Validators.maxLength(40)]],
    companyName: ['', [Validators.maxLength(200)]],
    addressLine1: ['', [Validators.maxLength(200)]],
    addressLine2: ['', [Validators.maxLength(200)]],
    city: ['', [Validators.maxLength(100)]],
    stateRegion: ['', [Validators.maxLength(100)]],
    postalCode: ['', [Validators.maxLength(20)]],
    countryCode: ['', [Validators.pattern(/^(?:[A-Za-z]{2})?$/), Validators.maxLength(2)]],
    taxId: ['', [Validators.maxLength(50)]],
    notes: ['', [Validators.maxLength(2000)]],
  });

  get isEdit(): boolean {
    return this.customerId() != null;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.customerId.set(id);
      this.load(id);
    } else {
      this.entitlements.refresh().subscribe((e) => {
        if (e && !this.entitlements.canCreateCustomer()) {
          this.planLimitHint.set(
            `You've reached the ${e.plan} plan limit of ${e.limits.activeCustomers.limit} active customers.`,
          );
          this.showViewPlans.set(true);
        }
      });
    }
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.toPayload();
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.showViewPlans.set(false);

    const request$ = this.isEdit
      ? this.api.update(this.customerId()!, payload)
      : this.api.create(payload);

    request$.subscribe({
      next: (customer) => {
        this.submitting.set(false);
        this.entitlements.refresh().subscribe();
        void this.router.navigateByUrl(`/app/customers/${customer.id}`);
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
    this.api
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => {
          this.form.patchValue({
            displayName: customer.displayName,
            email: customer.email ?? '',
            phone: customer.phone ?? '',
            companyName: customer.companyName ?? '',
            addressLine1: customer.addressLine1 ?? '',
            addressLine2: customer.addressLine2 ?? '',
            city: customer.city ?? '',
            stateRegion: customer.stateRegion ?? '',
            postalCode: customer.postalCode ?? '',
            countryCode: customer.countryCode ?? '',
            taxId: customer.taxId ?? '',
            notes: customer.notes ?? '',
          });
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.loadError.set(this.toSafeError(err));
        },
      });
  }

  private toPayload(): CustomerWritePayload {
    const v = this.form.getRawValue();
    const blank = (s: string) => (s.trim() ? s.trim() : null);
    const country = blank(v.countryCode);
    return {
      displayName: v.displayName.trim(),
      email: blank(v.email),
      phone: blank(v.phone),
      companyName: blank(v.companyName),
      addressLine1: blank(v.addressLine1),
      addressLine2: blank(v.addressLine2),
      city: blank(v.city),
      stateRegion: blank(v.stateRegion),
      postalCode: blank(v.postalCode),
      countryCode: country ? country.toUpperCase() : null,
      taxId: blank(v.taxId),
      notes: blank(v.notes),
    };
  }

  private toSafeError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) {
        return 'Network unavailable. Check your connection and try again.';
      }
      if (err.status === 404) {
        return 'Customer not found.';
      }
      if (err.status === 400 && err.error?.code === 'VALIDATION_ERROR') {
        return 'Please correct the highlighted fields and try again.';
      }
    }
    return 'Unable to save customer. Please try again.';
  }
}
