import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type AiCapabilities = {
  enabled: boolean;
  quoteAssistant: boolean;
  businessCopilot: boolean;
  aiActions?: boolean;
  provider: string;
  model: string;
};

export type BusinessCopilotReference = {
  type: 'CUSTOMER' | 'QUOTATION' | 'INVOICE' | 'PAYMENT' | string;
  id: string;
  displayNumber: string | null;
  label: string | null;
};

export type ActionProposalSummary = {
  proposalId: string;
  actionType: string;
  status: string;
  summary: string;
  expiresAt: string;
  confirmButtonLabel: string;
};

export type ActionProposalDetail = ActionProposalSummary & {
  createdAt: string;
  payload: Record<string, unknown>;
  preview: Record<string, unknown>;
  resultReferenceType: string | null;
  resultReferenceId: string | null;
};

export type ActionConfirmResponse = {
  proposalId: string;
  status: string;
  resultReferenceType: string | null;
  resultReferenceId: string | null;
  message: string;
};

export type BusinessCopilotResponse = {
  answer: string;
  references: BusinessCopilotReference[];
  warnings: string[];
  actionProposal: ActionProposalSummary | null;
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

  getProposal(proposalId: string): Observable<ActionProposalDetail> {
    return this.http.get<ActionProposalDetail>(`${this.base}/actions/${proposalId}`);
  }

  confirmProposal(proposalId: string): Observable<ActionConfirmResponse> {
    return this.http.post<ActionConfirmResponse>(`${this.base}/actions/${proposalId}/confirm`, {});
  }

  cancelProposal(proposalId: string): Observable<ActionProposalDetail> {
    return this.http.post<ActionProposalDetail>(`${this.base}/actions/${proposalId}/cancel`, {});
  }
}
