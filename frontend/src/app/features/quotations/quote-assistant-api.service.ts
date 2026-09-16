import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type AiCapabilities = {
  enabled: boolean;
  quoteAssistant: boolean;
  provider: string;
  model: string;
};

export type QuoteAssistantProposal = {
  customer: {
    matched: boolean;
    ambiguous: boolean;
    id: string | null;
    name: string | null;
    candidates: { id: string; name: string }[];
  };
  proposedCustomerName: string | null;
  items: {
    description: string;
    quantity: number | null;
    unitPrice: number | null;
    needsReview: boolean;
  }[];
  notes: string | null;
  discountType: string;
  discountValue: number;
  taxRate: number;
  warnings: string[];
  calculation: {
    subtotal: number;
    discountAmount: number;
    taxAmount: number;
    total: number;
    currency: string;
  } | null;
  calculationAvailable: boolean;
};

@Injectable({ providedIn: 'root' })
export class QuoteAssistantApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/ai`;

  capabilities(): Observable<AiCapabilities> {
    return this.http.get<AiCapabilities>(`${this.base}/capabilities`);
  }

  draft(prompt: string): Observable<QuoteAssistantProposal> {
    return this.http.post<QuoteAssistantProposal>(`${this.base}/quote-assistant/draft`, { prompt });
  }
}
