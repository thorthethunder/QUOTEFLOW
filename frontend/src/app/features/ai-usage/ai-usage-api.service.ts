import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AiUsageSummaryResponse } from './ai-usage.models';

@Injectable({ providedIn: 'root' })
export class AiUsageApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/ai/usage`;

  summary(): Observable<AiUsageSummaryResponse> {
    return this.http.get<AiUsageSummaryResponse>(this.baseUrl);
  }
}
