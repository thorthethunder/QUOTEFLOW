import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardSummary } from './dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/dashboard`;

  summary(from?: string, to?: string): Observable<DashboardSummary> {
    let params = new HttpParams();
    if (from && to) {
      params = params.set('from', from).set('to', to);
    }
    return this.http.get<DashboardSummary>(`${this.base}/summary`, { params });
  }
}
