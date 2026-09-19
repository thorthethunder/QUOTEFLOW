export type WorkflowStatus =
  | 'CREATED'
  | 'RUNNING'
  | 'WAITING_FOR_APPROVAL'
  | 'COMPLETED'
  | 'COMPLETED_WITH_PARTIAL_RESULTS'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED';

export type StepStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'WAITING_FOR_APPROVAL'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'SKIPPED'
  | 'EXPIRED';

export type WorkflowStep = {
  id: string;
  stepNumber: number;
  stepType: string;
  classification: 'READ_ONLY' | 'ACTION_REQUIRES_APPROVAL' | 'FORBIDDEN';
  status: StepStatus;
  actionProposalId: string | null;
  input: Record<string, unknown> | null;
  output: Record<string, unknown> | null;
  startedAt: string | null;
  completedAt: string | null;
  failureCode: string | null;
};

export type WorkflowSummary = {
  totalSteps: number;
  pendingApprovals: number;
  executedActions: number;
  failedActions: number;
  cancelledActions: number;
  expiredActions: number;
  partialResults: boolean;
};

export type AiWorkflow = {
  id: string;
  workflowType: 'PAYMENT_FOLLOW_UP';
  status: WorkflowStatus;
  goal: string;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  expiresAt: string;
  steps: WorkflowStep[];
  summary: WorkflowSummary;
};

export type AiWorkflowListItem = {
  id: string;
  workflowType: 'PAYMENT_FOLLOW_UP';
  status: WorkflowStatus;
  goal: string;
  updatedAt: string;
  expiresAt: string;
};
