import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, startWith, catchError } from 'rxjs';
import { EntitlementStore } from '../../plan/entitlement.store';
import { CustomerApiService } from '../customer-api.service';
import { CustomerSummary, PagedCustomers } from '../customer.models';

@Component({
  selector: 'app-customer-list',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './customer-list.html',
  styleUrl: './customer-list.scss',
})
export class CustomerListComponent implements OnInit {
  private readonly api = inject(CustomerApiService);
  private readonly entitlements = inject(EntitlementStore);
  private readonly destroyRef = inject(DestroyRef);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<'ACTIVE' | 'ARCHIVED'>('ACTIVE', {
    nonNullable: true,
  });

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly page = signal<PagedCustomers | null>(null);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly canCreate = signal(true);

  readonly displayedColumns = ['displayName', 'companyName', 'email', 'phone', 'status', 'actions'];

  ngOnInit(): void {
    this.entitlements.refresh().subscribe(() => {
      this.canCreate.set(this.entitlements.canCreateCustomer());
    });
    this.searchControl.valueChanges
      .pipe(
        startWith(this.searchControl.value),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.pageIndex.set(0);
        this.reload();
      });

    this.statusControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.pageIndex.set(0);
        this.reload();
      });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.reload();
  }

  retry(): void {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.api
      .list({
        q: this.searchControl.value,
        status: this.statusControl.value,
        page: this.pageIndex(),
        size: this.pageSize(),
        sort: 'displayName,asc',
      })
      .pipe(
        catchError((err: unknown) => {
          this.errorMessage.set(this.toSafeError(err));
          this.page.set(null);
          this.loading.set(false);
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (result) {
          this.page.set(result);
          this.loading.set(false);
        }
      });
  }

  trackById(_index: number, row: CustomerSummary): string {
    return row.id;
  }

  private toSafeError(err: unknown): string {
    if (err instanceof HttpErrorResponse && err.status === 0) {
      return 'Network unavailable. Check your connection and try again.';
    }
    if (err instanceof HttpErrorResponse && err.status === 429) {
      return 'Too many attempts. Please try again later.';
    }
    return 'Unable to load customers. Please try again.';
  }
}
