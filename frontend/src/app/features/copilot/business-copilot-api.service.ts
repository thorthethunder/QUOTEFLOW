import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type AiCapabilities = {
  enabled: boolean;
  quoteAssistant: boolean;
  businessCopilot: boolean;
  provider: string;
  model: string;
};

export type BusinessCopilotReference = {
  type: 'CUSTOMER' | 'QUOTATION' | 'INVOICE' | 'PAYMENT' | string;
  id: string;
  displayNumber: string | null;
  label: string | null;
};

export type BusinessCopilotResponse = {
  answer: string;
  references: BusinessCopilotReference[];
  warnings: string[];
};

@Injectable({ providedIn: 'root' })
export class BusinessCopilotApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/ai`;

  capabilities(): Observable<AiCapabilities> {
    return this.http.get<AiCapabilities>(`${this.base}/capabilities`);
  }

  ask(message: string): Observable<BusinessCopilotResponse> {
    return this.http.post<BusinessCopilotResponse>(`${this.base}/copilot/ask`, { message });
  }
}
