-- QuoteFlow AI Phase 4: controlled action proposals with human approval
-- Proposals are prepared by AI tools; execution only via authenticated confirm.

CREATE TABLE ai_action_proposals (
    id                      UUID            PRIMARY KEY,
    business_id             UUID            NOT NULL,
    requested_by_user_id    UUID            NOT NULL,
    action_type             VARCHAR(60)     NOT NULL,
    payload_json            TEXT            NOT NULL,
    payload_hash            VARCHAR(64)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    summary                 VARCHAR(500)    NOT NULL,
    preview_json            TEXT,
    created_at              TIMESTAMPTZ     NOT NULL,
    expires_at              TIMESTAMPTZ     NOT NULL,
    executed_at             TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    result_reference_type   VARCHAR(40),
    result_reference_id     UUID,
    failure_code            VARCHAR(80),
    version                 BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_action_proposals_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_action_proposals_user
        FOREIGN KEY (requested_by_user_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_ai_action_proposals_status
        CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED', 'EXPIRED', 'FAILED')),
    CONSTRAINT chk_ai_action_proposals_type
        CHECK (action_type IN (
            'QUOTATION_CREATE_DRAFT',
            'INVOICE_CREATE_DRAFT',
            'REMINDER_PREPARE'
        ))
);

CREATE INDEX idx_ai_action_proposals_business_status
    ON ai_action_proposals (business_id, status, created_at DESC);

CREATE INDEX idx_ai_action_proposals_user_status
    ON ai_action_proposals (requested_by_user_id, status, created_at DESC);

CREATE INDEX idx_ai_action_proposals_expires
    ON ai_action_proposals (status, expires_at);
