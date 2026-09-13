import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { NotificationItem } from '../../notifications/notification.models';
import { EntitlementStore } from '../../plan/entitlement.store';
import { InvoiceApiService } from '../../invoices/invoice-api.service';
import { QuotationApiService } from '../quotation-api.service';
import { Quotation } from '../quotation.models';
import { ConfirmQuotationActionDialog } from './confirm-quotation-action-dialog';
import { SendQuotationEmailDialog } from './send-quotation-email-dialog';

@Component({
  selector: 'app-quotation-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './quotation-detail.html',
  styleUrl: './quotation-detail.scss',
})
export class QuotationDetailComponent implements OnInit {
  private readonly api = inject(QuotationApiService);
  private readonly invoiceApi = inject(InvoiceApiService);
  private readonly notificationApi = inject(NotificationApiService);
  private readonly entitlements = inject(EntitlementStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(true);
  readonly acting = signal(false);
  readonly downloadingPdf = signal(false);
  readonly emailBusy = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly pdfError = signal<string | null>(null);
  readonly emailMessage = signal<string | null>(null);
  readonly quotation = signal<Quotation | null>(null);
  readonly notifications = signal<NotificationItem[]>([]);
  readonly invoiceLimitReached = signal(false);
  readonly showViewPlans = signal(false);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Quotation not found.');
      this.loading.set(false);
      return;
    }
    this.load(id);
    this.entitlements.refresh().subscribe(() => {
      this.invoiceLimitReached.set(!this.entitlements.canCreateInvoice());
    });
  }

  markSent(): void {
    const q = this.quotation();
    if (!q || q.status !== 'DRAFT') {
      return;
    }
    this.confirm('Mark as sent?', 'This locks the quotation. Use Send email to email the customer.', () =>
      this.api.markSent(q.id, q.version),
    );
  }

  openSendEmail(): void {
    const q = this.quotation();
    if (!q || q.status === 'CANCELLED' || this.emailBusy()) {
      return;
    }
    const latest = this.notifications()[0];
    const mode =
      latest?.status === 'FAILED' ? 'retry' : latest?.status === 'SENT' ? 'resend' : 'send';
    const ref = this.dialog.open(SendQuotationEmailDialog, {
      width: 'min(28rem, 94vw)',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
      data: {
        quotationNumber: q.quotationNumber,
        recipientEmail: q.customerEmail,
        emailSendingEnabled: this.entitlements.canSendEmail(),
        mode,
      },
    });
    ref.afterClosed().subscribe((result) => {
      if (!result || this.emailBusy()) {
        return;
      }
      this.emailBusy.set(true);
      this.emailMessage.set(null);
      this.actionError.set(null);
      this.showViewPlans.set(false);
      this.notificationApi
        .sendQuotationEmail(q.id, {
          message: result.message || undefined,
          version: q.version,
          resend: !!result.resend,
        })
        .subscribe({
          next: (notification) => {
            this.emailBusy.set(false);
            if (notification.status === 'SENT') {
              this.emailMessage.set(
                mode === 'resend' ? 'Quotation email resent.' : 'Quotation email sent.',
              );
            } else if (notification.status === 'FAILED') {
              this.emailMessage.set('Email could not be sent. Retry.');
            } else {
              this.emailMessage.set('Quotation email queued.');
            }
            this.load(q.id);
          },
          error: (err: unknown) => {
            this.emailBusy.set(false);
            const planMsg = this.entitlements.messageFromApiError(err);
            if (planMsg) {
              this.actionError.set(planMsg);
              this.showViewPlans.set(true);
              return;
            }
            if (err instanceof HttpErrorResponse && err.error?.code === 'RECIPIENT_EMAIL_MISSING') {
              this.actionError.set('Add an email address to this customer first.');
              return;
            }
            this.actionError.set('Unable to send quotation email. Please try again.');
          },
        });
    });
  }

  cancel(): void {
    const q = this.quotation();
    if (!q || q.status === 'CANCELLED') {
      return;
    }
    this.confirm('Cancel quotation?', 'The quotation remains visible for history.', () =>
      this.api.cancel(q.id, q.version),
    );
  }

  convertToInvoice(): void {
    const q = this.quotation();
    if (!q || q.status !== 'SENT' || q.convertedInvoiceId || this.acting()) {
      return;
    }
    const ref = this.dialog.open(ConfirmQuotationActionDialog, {
      width: 'min(24rem, 92vw)',
      data: {
        title: 'Convert to Invoice?',
        body: 'Creates a draft invoice from this sent quotation. The quotation cannot be converted again.',
        confirmLabel: 'Convert to Invoice',
      },
    });
    ref.afterClosed().subscribe((ok) => {
      if (!ok || this.acting()) {
        return;
      }
      this.acting.set(true);
      this.invoiceApi.convertFromQuotation(q.id).subscribe({
        next: (invoice) => {
          this.acting.set(false);
          void this.router.navigateByUrl(`/app/invoices/${invoice.id}`);
        },
        error: (err: unknown) => {
          this.acting.set(false);
          if (err instanceof HttpErrorResponse && err.status === 409) {
            const existingId = err.error?.details?.existingInvoiceId as string | undefined;
            if (existingId) {
              void this.router.navigateByUrl(`/app/invoices/${existingId}`);
              return;
            }
          }
          const planMsg = this.entitlements.messageFromApiError(err);
          if (planMsg) {
            this.actionError.set(planMsg);
            this.showViewPlans.set(true);
            this.invoiceLimitReached.set(true);
            this.entitlements.refresh().subscribe();
            return;
          }
          this.actionError.set('Unable to convert quotation to invoice. Please try again.');
        },
      });
    });
  }

  downloadPdf(): void {
    const q = this.quotation();
    if (!q || this.downloadingPdf()) {
      return;
    }
    this.downloadingPdf.set(true);
    this.pdfError.set(null);
    this.api.downloadPdf(q.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${q.quotationNumber.replace(/[^A-Za-z0-9._-]/g, '_')}.pdf`;
        anchor.rel = 'noopener';
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
        this.downloadingPdf.set(false);
      },
      error: () => {
        this.downloadingPdf.set(false);
        this.pdfError.set('Unable to download PDF. Please try again.');
      },
    });
  }

  private confirm(title: string, body: string, action: () => import('rxjs').Observable<Quotation>): void {
    const ref = this.dialog.open(ConfirmQuotationActionDialog, {
      width: 'min(24rem, 92vw)',
      data: { title, body, confirmLabel: title.startsWith('Cancel') ? 'Cancel quotation' : 'Mark as sent' },
    });
    ref.afterClosed().subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.acting.set(true);
      action().subscribe({
        next: (updated) => {
          this.quotation.set(updated);
          this.acting.set(false);
        },
        error: () => {
          this.acting.set(false);
          this.errorMessage.set('Unable to update quotation status. Please try again.');
        },
      });
    });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (quotation) => {
        this.quotation.set(quotation);
        this.loading.set(false);
        this.loadNotifications(id);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof HttpErrorResponse && err.status === 404
            ? 'Quotation not found.'
            : 'Unable to load quotation.',
        );
      },
    });
  }

  private loadNotifications(id: string): void {
    this.notificationApi.listQuotationNotifications(id).subscribe({
      next: (items) => this.notifications.set(items),
      error: () => this.notifications.set([]),
    });
  }
}
