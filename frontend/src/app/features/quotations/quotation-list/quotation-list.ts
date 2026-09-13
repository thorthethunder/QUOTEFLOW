import { CurrencyPipe, DatePipe } from '@angular/common';
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
import { catchError, debounceTime, distinctUntilChanged, of, startWith } from 'rxjs';
import { EntitlementStore } from '../../plan/entitlement.store';
import { QuotationApiService } from '../quotation-api.service';
import { PagedQuotations, QuotationSummary } from '../quotation.models';

@Component({
  selector: 'app-quotation-list',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './quotation-list.html',
  styleUrl: './quotation-list.scss',
})
export class QuotationListComponent implements OnInit {
  private readonly api = inject(QuotationApiService);
  private readonly entitlements = inject(EntitlementStore);
  private readonly destroyRef = inject(DestroyRef);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<string>('', { nonNullable: true });
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly page = signal<PagedQuotations | null>(null);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly canCreate = signal(true);
  readonly displayedColumns = ['quotationNumber', 'customer', 'issueDate', 'status', 'total', 'actions'];

  ngOnInit(): void {
    this.entitlements.refresh().subscribe(() => {
      this.canCreate.set(this.entitlements.canCreateQuotation());
    });
    this.searchControl.valueChanges
      .pipe(startWith(''), debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.pageIndex.set(0);
        this.reload();
      });
    this.statusControl.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
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

  trackById(_: number, row: QuotationSummary): string {
    return row.id;
  }

  private reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .list({
        q: this.searchControl.value,
        status: this.statusControl.value || undefined,
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .pipe(
        catchError((err: unknown) => {
          this.errorMessage.set(
            err instanceof HttpErrorResponse && err.status === 0
              ? 'Network unavailable. Please try again.'
              : 'Unable to load quotations. Please try again.',
          );
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
}
