-- QuoteFlow Phase 13: transactional email notification outbox
-- Flyway owns schema. Do not edit V1–V9.
-- Notifications are secondary to business records (quotations/invoices/payments).

CREATE TABLE notifications (
    id                      UUID            PRIMARY KEY,
    business_id             UUID            NOT NULL,
    type                    VARCHAR(60)     NOT NULL,
    channel                 VARCHAR(20)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    recipient_email         VARCHAR(320)    NOT NULL,
    recipient_display_name  VARCHAR(200),
    subject                 VARCHAR(300)    NOT NULL,
    template_key            VARCHAR(80)     NOT NULL,
    template_vars_json      TEXT            NOT NULL,
    reference_type          VARCHAR(40)     NOT NULL,
    reference_id            UUID            NOT NULL,
    attempt_count           INT             NOT NULL DEFAULT 0,
    max_attempts            INT             NOT NULL DEFAULT 4,
    next_attempt_at         TIMESTAMPTZ     NOT NULL,
    last_error_code         VARCHAR(80),
    provider                VARCHAR(40),
    provider_message_id     VARCHAR(200),
    idempotency_key         VARCHAR(120),
    reply_to_email          VARCHAR(320),
    include_pdf_attachment  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ     NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL,
    sent_at                 TIMESTAMPTZ,
    CONSTRAINT fk_notifications_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT chk_notifications_channel
        CHECK (channel IN ('EMAIL')),
    CONSTRAINT chk_notifications_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_notifications_type
        CHECK (type IN (
            'QUOTATION_EMAIL',
            'INVOICE_REMINDER',
            'RECEIPT_EMAIL',
            'PLATFORM_NOTICE'
        )),
    CONSTRAINT chk_notifications_attempts
        CHECK (attempt_count >= 0 AND max_attempts >= 1 AND attempt_count <= max_attempts)
);

CREATE INDEX idx_notifications_claim
    ON notifications (status, next_attempt_at, created_at);

CREATE INDEX idx_notifications_business_created
    ON notifications (business_id, created_at DESC);

CREATE INDEX idx_notifications_reference
    ON notifications (business_id, reference_type, reference_id, created_at DESC);

CREATE UNIQUE INDEX uq_notifications_idempotency
    ON notifications (business_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX uq_notifications_active_reference
    ON notifications (business_id, type, reference_id)
    WHERE status IN ('PENDING', 'SENDING');
