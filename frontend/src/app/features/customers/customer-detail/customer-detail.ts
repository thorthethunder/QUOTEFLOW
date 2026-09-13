import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CustomerApiService } from '../customer-api.service';
import { Customer } from '../customer.models';
import { ConfirmArchiveDialogComponent } from './confirm-archive-dialog';

@Component({
  selector: 'app-customer-detail',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './customer-detail.html',
  styleUrl: './customer-detail.scss',
})
export class CustomerDetailComponent implements OnInit {
  private readonly api = inject(CustomerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly archiving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly customer = signal<Customer | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Customer not found.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  archive(): void {
    const current = this.customer();
    if (!current || current.status === 'ARCHIVED' || this.archiving()) {
      return;
    }

    const ref = this.dialog.open(ConfirmArchiveDialogComponent, {
      data: { displayName: current.displayName },
      width: 'min(24rem, 92vw)',
    });

    ref
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.archiving.set(true);
        this.api
          .archive(current.id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (updated) => {
              this.customer.set(updated);
              this.archiving.set(false);
            },
            error: () => {
              this.archiving.set(false);
              this.errorMessage.set('Unable to archive customer. Please try again.');
            },
          });
      });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.api
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => {
          this.customer.set(customer);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          if (err instanceof HttpErrorResponse && err.status === 404) {
            this.errorMessage.set('Customer not found.');
          } else {
            this.errorMessage.set('Unable to load customer. Please try again.');
          }
        },
      });
  }
}
