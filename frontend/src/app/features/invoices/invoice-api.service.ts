import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Invoice, InvoiceWritePayload, PagedInvoices } from './invoice.models';

@Injectable({ providedIn: 'root' })
export class InvoiceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/invoices`;
  private readonly quotationsBase = `${environment.apiBaseUrl}/quotations`;

  list(params: {
    q?: string;
    status?: string;
    page?: number;
    size?: number;
    sort?: string;
  } = {}): Observable<PagedInvoices> {
    let httpParams = new HttpParams()
      .set('page', String(params.page ?? 0))
      .set('size', String(params.size ?? 20))
      .set('sort', params.sort ?? 'createdAt,desc');
    if (params.q?.trim()) {
      httpParams = httpParams.set('q', params.q.trim());
    }
    if (params.status) {
      httpParams = httpParams.set('status', params.status);
    }
    return this.http.get<PagedInvoices>(this.base, { params: httpParams });
  }

  get(id: string): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.base}/${id}`);
  }

  create(payload: InvoiceWritePayload): Observable<Invoice> {
    return this.http.post<Invoice>(this.base, payload);
  }

  update(id: string, payload: InvoiceWritePayload): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.base}/${id}`, payload);
  }

  markSent(id: string, version: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.base}/${id}/send`, { version });
  }

  cancel(id: string, version: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.base}/${id}/cancel`, { version });
  }

  convertFromQuotation(quotationId: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.quotationsBase}/${quotationId}/convert-to-invoice`, {});
  }
}
