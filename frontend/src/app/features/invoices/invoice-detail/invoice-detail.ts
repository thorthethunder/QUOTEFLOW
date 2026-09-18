import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { NotificationItem } from '../../notifications/notification.models';
import { EntitlementStore } from '../../plan/entitlement.store';
import { PaymentApiService } from '../../payments/payment-api.service';
import {
  Payment,
  PaymentSummary,
  paymentMethodLabel,
  paymentStateLabel,
  paymentStatusLabel,
} from '../../payments/payment.models';
import { RecordPaymentDialog } from '../../payments/record-payment-dialog';
import { VoidPaymentDialog } from '../../payments/void-payment-dialog';
import { InvoiceApiService } from '../invoice-api.service';
import { Invoice } from '../invoice.models';
import { ConfirmInvoiceActionDialog } from './confirm-invoice-action-dialog';
import { PrepareAiReminderDialog } from './prepare-ai-reminder-dialog';
import { SendReminderDialog } from './send-reminder-dialog';
import { BusinessCopilotApiService } from '../../copilot/business-copilot-api.service';
import { ActionConfirmResponse } from '../../copilot/business-copilot-api.service';

@Component({
  selector: 'app-invoice-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './invoice-detail.html',
  styleUrl: './invoice-detail.scss',
})
export class InvoiceDetailComponent implements OnInit {
  private readonly api = inject(InvoiceApiService);
  private readonly paymentsApi = inject(PaymentApiService);
  private readonly notificationApi = inject(NotificationApiService);
  private readonly entitlements = inject(EntitlementStore);
  private readonly copilotApi = inject(BusinessCopilotApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(true);
  readonly acting = signal(false);
  readonly reminderBusy = signal(false);
  readonly aiActionsEnabled = signal(false);
  readonly downloadingReceiptId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly paymentError = signal<string | null>(null);
  readonly reminderMessage = signal<string | null>(null);
  readonly invoice = signal<Invoice | null>(null);
  readonly paymentSummary = signal<PaymentSummary | null>(null);
  readonly payments = signal<Payment[]>([]);
  readonly notifications = signal<NotificationItem[]>([]);
  readonly paymentColumns = [
    'receipt',
    'date',
    'method',
    'amount',
    'status',
    'actions',
  ];

  readonly paymentStateLabel = paymentStateLabel;
  readonly paymentMethodLabel = paymentMethodLabel;
  readonly paymentStatusLabel = paymentStatusLabel;

  paymentProgress(amountPaid: number | string, totalAmount: number | string): number {
    const paid = Number(amountPaid);
    const total = Number(totalAmount);
    if (!Number.isFinite(paid) || !Number.isFinite(total) || total <= 0) {
      return 0;
    }
    return Math.min(100, Math.max(0, (paid / total) * 100));
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Invoice not found.');
      this.loading.set(false);
      return;
    }
    this.entitlements.refresh().subscribe();
    this.copilotApi.capabilities().subscribe({
      next: (caps) => this.aiActionsEnabled.set(!!caps.aiActions),
      error: () => this.aiActionsEnabled.set(false),
    });
    this.load(id);
  }

  canRecordPayment(): boolean {
    const inv = this.invoice();
    const summary = this.paymentSummary() ?? inv?.paymentSummary ?? null;
    return inv?.status === 'SENT' && !!summary && summary.balanceDue > 0;
  }

  canSendReminder(): boolean {
    return this.canRecordPayment();
  }

  openPrepareAiReminder(): void {
    const inv = this.invoice();
    const summary = this.paymentSummary() ?? inv?.paymentSummary;
    if (!inv || !summary || !this.canSendReminder()) {
      return;
    }
    const ref = this.dialog.open(PrepareAiReminderDialog, {
      width: 'min(36rem, 96vw)',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
      data: {
        invoiceId: inv.id,
        invoiceNumber: inv.invoiceNumber,
        recipientEmail: inv.customerEmail,
        balanceDue: summary.balanceDue,
        currency: inv.currency,
        customerDisplayName: inv.customerDisplayName,
        aiActionsEnabled: this.aiActionsEnabled(),
        emailSendingEnabled: this.entitlements.canSendEmail(),
      },
    });
    ref.afterClosed().subscribe((result) => {
      if (!result) {
        return;
      }
      const confirmed = result as ActionConfirmResponse;
      this.reminderMessage.set(confirmed.message || 'Payment reminder queued for delivery.');
      this.notificationApi.listInvoiceNotifications(inv.id).subscribe({
        next: (items) => this.notifications.set(items),
      });
    });
  }

  openSendReminder(): void {
    const inv = this.invoice();
    const summary = this.paymentSummary() ?? inv?.paymentSummary;
    if (!inv || !summary || !this.canSendReminder() || this.reminderBusy()) {
      return;
    }
    const latest = this.notifications()[0];
    const mode =
      latest?.status === 'FAILED' ? 'retry' : latest?.status === 'SENT' ? 'resend' : 'send';
    const ref = this.dialog.open(SendReminderDialog, {
      width: 'min(28rem, 94vw)',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
      data: {
        invoiceNumber: inv.invoiceNumber,
        recipientEmail: inv.customerEmail,
        balanceDue: summary.balanceDue,
        currency: inv.currency,
        emailSendingEnabled: this.entitlements.canSendEmail(),
        mode,
      },
    });
    ref.afterClosed().subscribe((result) => {
      if (!result || this.reminderBusy()) {
        return;
      }
      this.reminderBusy.set(true);
      this.reminderMessage.set(null);
      this.notificationApi
        .sendInvoiceReminder(inv.id, {
          tone: result.tone,
          message: result.message || undefined,
          resend: !!result.resend,
        })
        .subscribe({
          next: (notification) => {
            this.reminderBusy.set(false);
            this.reminderMessage.set(
              notification.status === 'SENT'
                ? mode === 'resend'
                  ? 'Reminder resent.'
                  : 'Reminder sent.'
                : notification.status === 'FAILED'
                  ? 'Reminder could not be sent. Retry.'
                  : 'Reminder queued.',
            );
            this.notificationApi.listInvoiceNotifications(inv.id).subscribe({
              next: (items) => this.notifications.set(items),
            });
          },
          error: (err: unknown) => {
            this.reminderBusy.set(false);
            const planMsg = this.entitlements.messageFromApiError(err);
            this.reminderMessage.set(planMsg ?? 'Unable to send reminder. Please try again.');
          },
        });
    });
  }

  markSent(): void {
    const invoice = this.invoice();
    if (!invoice || invoice.status !== 'DRAFT') {
      return;
    }
    this.confirm('Mark as Sent?', 'This locks the invoice. Use Send reminder after it is outstanding.', 'Mark as Sent', () =>
      this.api.markSent(invoice.id, invoice.version),
    );
  }

  cancel(): void {
    const invoice = this.invoice();
    if (!invoice || invoice.status === 'CANCELLED') {
      return;
    }
    this.confirm('Cancel invoice?', 'The invoice remains visible for history.', 'Cancel invoice', () =>
      this.api.cancel(invoice.id, invoice.version),
    );
  }

  openRecordPayment(): void {
    const inv = this.invoice();
    const summary = this.paymentSummary() ?? inv?.paymentSummary;
    if (!inv || !summary || !this.canRecordPayment()) {
      return;
    }
    const ref = this.dialog.open(RecordPaymentDialog, {
      width: 'min(28rem, 94vw)',
      data: {
        invoiceId: inv.id,
        currency: inv.currency,
        balanceDue: summary.balanceDue,
      },
    });
    ref.afterClosed().subscribe((ok) => {
      if (ok) {
        this.refreshPayments(inv.id);
      }
    });
  }

  downloadReceipt(payment: Payment): void {
    if (this.downloadingReceiptId()) {
      return;
    }
    this.downloadingReceiptId.set(payment.id);
    this.paymentError.set(null);
    this.paymentsApi.downloadReceipt(payment.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${payment.receiptNumber.replace(/[^A-Za-z0-9._-]/g, '_')}.pdf`;
        anchor.rel = 'noopener';
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
        this.downloadingReceiptId.set(null);
      },
      error: () => {
        this.downloadingReceiptId.set(null);
        this.paymentError.set('Unable to download receipt. Please try again.');
      },
    });
  }

  openVoidPayment(payment: Payment): void {
    if (payment.status !== 'RECORDED') {
      return;
    }
    const inv = this.invoice();
    if (!inv) {
      return;
    }
    const ref = this.dialog.open(VoidPaymentDialog, {
      width: 'min(28rem, 94vw)',
      data: { payment },
    });
    ref.afterClosed().subscribe((ok) => {
      if (ok) {
        this.refreshPayments(inv.id);
      }
    });
  }

  private confirm(
    title: string,
    body: string,
    confirmLabel: string,
    action: () => import('rxjs').Observable<Invoice>,
  ): void {
    if (this.acting()) {
      return;
    }
    const ref = this.dialog.open(ConfirmInvoiceActionDialog, {
      width: 'min(24rem, 92vw)',
      data: { title, body, confirmLabel },
    });
    ref.afterClosed().subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.acting.set(true);
      action().subscribe({
        next: (updated) => {
          this.invoice.set(updated);
          if (updated.paymentSummary) {
            this.paymentSummary.set(updated.paymentSummary);
          }
          this.acting.set(false);
        },
        error: () => {
          this.acting.set(false);
          this.errorMessage.set('Unable to update invoice status. Please try again.');
        },
      });
    });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.paymentError.set(null);
    forkJoin({
      invoice: this.api.get(id),
      payments: this.paymentsApi.listForInvoice(id),
    }).subscribe({
      next: ({ invoice, payments }) => {
        this.invoice.set(invoice);
        this.paymentSummary.set(payments.summary);
        this.payments.set(payments.payments);
        this.loading.set(false);
        this.notificationApi.listInvoiceNotifications(id).subscribe({
          next: (items) => this.notifications.set(items),
          error: () => this.notifications.set([]),
        });
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof HttpErrorResponse && err.status === 404
            ? 'Invoice not found.'
            : 'Unable to load invoice.',
        );
      },
    });
  }

  /** Refresh invoice + payments from server after payment mutations (API is source of truth). */
  private refreshPayments(invoiceId: string): void {
    this.paymentError.set(null);
    forkJoin({
      invoice: this.api.get(invoiceId),
      payments: this.paymentsApi.listForInvoice(invoiceId),
    }).subscribe({
      next: ({ invoice, payments }) => {
        this.invoice.set(invoice);
        this.paymentSummary.set(payments.summary);
        this.payments.set(payments.payments);
      },
      error: () => {
        this.paymentError.set('Payment saved, but unable to refresh. Reload the page.');
      },
    });
  }
}
