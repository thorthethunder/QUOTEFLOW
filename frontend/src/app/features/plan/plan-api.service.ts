import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EntitlementResponse, PlanCatalogItem } from './plan.models';

@Injectable({ providedIn: 'root' })
export class PlanApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  getEntitlements(): Observable<EntitlementResponse> {
    return this.http.get<EntitlementResponse>(`${this.base}/subscription`);
  }

  getCatalog(): Observable<PlanCatalogItem[]> {
    return this.http.get<PlanCatalogItem[]>(`${this.base}/plans`);
  }
}
