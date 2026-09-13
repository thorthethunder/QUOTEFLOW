import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PagedQuotations, Quotation, QuotationWritePayload } from './quotation.models';

@Injectable({ providedIn: 'root' })
export class QuotationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/quotations`;

  list(params: {
    q?: string;
    status?: string;
    page?: number;
    size?: number;
    sort?: string;
  } = {}): Observable<PagedQuotations> {
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
    return this.http.get<PagedQuotations>(this.base, { params: httpParams });
  }

  get(id: string): Observable<Quotation> {
    return this.http.get<Quotation>(`${this.base}/${id}`);
  }

  create(payload: QuotationWritePayload): Observable<Quotation> {
    return this.http.post<Quotation>(this.base, payload);
  }

  update(id: string, payload: QuotationWritePayload): Observable<Quotation> {
    return this.http.put<Quotation>(`${this.base}/${id}`, payload);
  }

  markSent(id: string, version: number): Observable<Quotation> {
    return this.http.post<Quotation>(`${this.base}/${id}/send`, { version });
  }

  cancel(id: string, version: number): Observable<Quotation> {
    return this.http.post<Quotation>(`${this.base}/${id}/cancel`, { version });
  }

  downloadPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' });
  }
}
