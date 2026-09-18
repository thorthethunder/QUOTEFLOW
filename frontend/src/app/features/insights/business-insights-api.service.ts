import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ReportingInsightResponse } from './business-insights.models';

@Injectable({ providedIn: 'root' })
export class BusinessInsightsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/ai/insights`;

  analyze(question: string, period = 'THIS_MONTH'): Observable<ReportingInsightResponse> {
    return this.http.post<ReportingInsightResponse>(`${this.base}/analyze`, {
      question,
      period,
      comparison: 'PREVIOUS_PERIOD',
    });
  }
}
