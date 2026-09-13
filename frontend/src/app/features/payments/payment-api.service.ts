import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreatePaymentPayload,
  InvoicePaymentsResponse,
  Payment,
  VoidPaymentPayload,
} from './payment.models';

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly http = inject(HttpClient);
  private readonly invoicesBase = `${environment.apiBaseUrl}/invoices`;
  private readonly paymentsBase = `${environment.apiBaseUrl}/payments`;

  listForInvoice(invoiceId: string): Observable<InvoicePaymentsResponse> {
    return this.http.get<InvoicePaymentsResponse>(`${this.invoicesBase}/${invoiceId}/payments`);
  }

  create(invoiceId: string, payload: CreatePaymentPayload): Observable<Payment> {
    return this.http.post<Payment>(`${this.invoicesBase}/${invoiceId}/payments`, payload);
  }

  get(paymentId: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.paymentsBase}/${paymentId}`);
  }

  voidPayment(paymentId: string, payload: VoidPaymentPayload = {}): Observable<Payment> {
    return this.http.post<Payment>(`${this.paymentsBase}/${paymentId}/void`, payload);
  }

  downloadReceipt(paymentId: string): Observable<Blob> {
    return this.http.get(`${this.paymentsBase}/${paymentId}/receipt.pdf`, {
      responseType: 'blob',
    });
  }
}
