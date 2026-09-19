import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AiWorkflow, AiWorkflowListItem } from './ai-workflows.models';
import { ActionConfirmResponse, ActionProposalDetail } from '../copilot/business-copilot-api.service';

@Injectable({ providedIn: 'root' })
export class AiWorkflowsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/ai`;

  list(): Observable<AiWorkflowListItem[]> {
    return this.http.get<AiWorkflowListItem[]>(`${this.base}/workflows`);
  }

  startPaymentFollowUp(input: {
    goal: string;
    maxItems: number;
    idempotencyKey: string;
  }): Observable<AiWorkflow> {
    return this.http.post<AiWorkflow>(`${this.base}/workflows`, {
      workflowType: 'PAYMENT_FOLLOW_UP',
      ...input,
    });
  }

  get(workflowId: string): Observable<AiWorkflow> {
    return this.http.get<AiWorkflow>(`${this.base}/workflows/${workflowId}`);
  }

  resume(workflowId: string): Observable<AiWorkflow> {
    return this.http.post<AiWorkflow>(`${this.base}/workflows/${workflowId}/resume`, {});
  }

  cancel(workflowId: string): Observable<AiWorkflow> {
    return this.http.post<AiWorkflow>(`${this.base}/workflows/${workflowId}/cancel`, {});
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
