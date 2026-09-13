import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  NotificationItem,
  SendInvoiceReminderPayload,
  SendQuotationEmailPayload,
} from './notification.models';

@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly quotationsBase = `${environment.apiBaseUrl}/quotations`;
  private readonly invoicesBase = `${environment.apiBaseUrl}/invoices`;
  private readonly notificationsBase = `${environment.apiBaseUrl}/notifications`;

  sendQuotationEmail(quotationId: string, payload: SendQuotationEmailPayload = {}): Observable<NotificationItem> {
    return this.http.post<NotificationItem>(`${this.quotationsBase}/${quotationId}/send-email`, payload);
  }

  listQuotationNotifications(quotationId: string): Observable<NotificationItem[]> {
    return this.http.get<NotificationItem[]>(`${this.quotationsBase}/${quotationId}/notifications`);
  }

  sendInvoiceReminder(invoiceId: string, payload: SendInvoiceReminderPayload = {}): Observable<NotificationItem> {
    return this.http.post<NotificationItem>(`${this.invoicesBase}/${invoiceId}/send-reminder`, payload);
  }

  listInvoiceNotifications(invoiceId: string): Observable<NotificationItem[]> {
    return this.http.get<NotificationItem[]>(`${this.invoicesBase}/${invoiceId}/notifications`);
  }

  get(id: string): Observable<NotificationItem> {
    return this.http.get<NotificationItem>(`${this.notificationsBase}/${id}`);
  }
}
