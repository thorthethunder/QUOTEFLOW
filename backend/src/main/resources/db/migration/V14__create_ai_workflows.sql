CREATE TABLE ai_workflows (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    requested_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    workflow_type VARCHAR(60) NOT NULL,
    status VARCHAR(40) NOT NULL,
    goal VARCHAR(1000) NOT NULL,
    idempotency_key VARCHAR(120),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ai_workflows_status_ck CHECK (status IN (
        'CREATED',
        'RUNNING',
        'WAITING_FOR_APPROVAL',
        'COMPLETED',
        'COMPLETED_WITH_PARTIAL_RESULTS',
        'FAILED',
        'CANCELLED',
        'EXPIRED'
    )),
    CONSTRAINT ai_workflows_type_ck CHECK (workflow_type IN ('PAYMENT_FOLLOW_UP'))
);

CREATE UNIQUE INDEX ai_workflows_idempotency_uk
    ON ai_workflows (business_id, requested_by_user_id, workflow_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_ai_workflows_business_status
    ON ai_workflows (business_id, status, updated_at DESC);

CREATE TABLE ai_workflow_steps (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES ai_workflows(id) ON DELETE CASCADE,
    step_number INTEGER NOT NULL,
    step_type VARCHAR(80) NOT NULL,
    classification VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    action_proposal_id UUID REFERENCES ai_action_proposals(id) ON DELETE SET NULL,
    input_json TEXT NOT NULL,
    output_json TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ai_workflow_steps_status_ck CHECK (status IN (
        'PENDING',
        'RUNNING',
        'WAITING_FOR_APPROVAL',
        'COMPLETED',
        'FAILED',
        'CANCELLED',
        'SKIPPED',
        'EXPIRED'
    )),
    CONSTRAINT ai_workflow_steps_type_ck CHECK (step_type IN (
        'READ_OUTSTANDING_INVOICES',
        'PREPARE_PAYMENT_REMINDER',
        'OBSERVE_ACTION_RESULT'
    )),
    CONSTRAINT ai_workflow_steps_classification_ck CHECK (classification IN (
        'READ_ONLY',
        'ACTION_REQUIRES_APPROVAL',
        'FORBIDDEN'
    ))
);

CREATE UNIQUE INDEX ai_workflow_steps_number_uk
    ON ai_workflow_steps (workflow_id, step_number);

CREATE UNIQUE INDEX ai_workflow_steps_action_proposal_uk
    ON ai_workflow_steps (action_proposal_id)
    WHERE action_proposal_id IS NOT NULL;

CREATE INDEX idx_ai_workflow_steps_workflow_status
    ON ai_workflow_steps (workflow_id, status, step_number);
