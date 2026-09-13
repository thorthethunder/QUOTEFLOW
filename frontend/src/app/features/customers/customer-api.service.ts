import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Customer,
  CustomerListParams,
  CustomerWritePayload,
  PagedCustomers,
} from './customer.models';

@Injectable({ providedIn: 'root' })
export class CustomerApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/customers`;

  list(params: CustomerListParams = {}): Observable<PagedCustomers> {
    let httpParams = new HttpParams()
      .set('page', String(params.page ?? 0))
      .set('size', String(params.size ?? 20))
      .set('sort', params.sort ?? 'displayName,asc');

    if (params.q?.trim()) {
      httpParams = httpParams.set('q', params.q.trim());
    }
    if (params.status) {
      httpParams = httpParams.set('status', params.status);
    }

    return this.http.get<PagedCustomers>(this.base, { params: httpParams });
  }

  get(id: string): Observable<Customer> {
    return this.http.get<Customer>(`${this.base}/${id}`);
  }

  create(payload: CustomerWritePayload): Observable<Customer> {
    return this.http.post<Customer>(this.base, payload);
  }

  update(id: string, payload: CustomerWritePayload): Observable<Customer> {
    return this.http.put<Customer>(`${this.base}/${id}`, payload);
  }

  archive(id: string): Observable<Customer> {
    return this.http.post<Customer>(`${this.base}/${id}/archive`, {});
  }
}
