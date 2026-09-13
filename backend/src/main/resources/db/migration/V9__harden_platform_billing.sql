-- QuoteFlow Phase 12: platform billing hardening (Razorpay SaaS subscriptions)
-- Flyway owns schema. Do not edit V1–V8.
-- Platform billing remains separate from tenant Invoice/Payment tables.

-- Extend subscriptions with billing-interval and provider lifecycle fields.
ALTER TABLE subscriptions
    ADD COLUMN billing_interval VARCHAR(20),
    ADD COLUMN provider_plan_id VARCHAR(200),
    ADD COLUMN provider_status VARCHAR(40),
    ADD COLUMN last_provider_event_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscriptions_billing_interval
        CHECK (billing_interval IS NULL OR billing_interval IN ('MONTHLY', 'ANNUAL'));

CREATE UNIQUE INDEX uq_subscriptions_provider_subscription_id
    ON subscriptions (provider_subscription_id)
    WHERE provider_subscription_id IS NOT NULL;

CREATE INDEX idx_subscriptions_provider_status
    ON subscriptions (provider_status)
    WHERE provider_status IS NOT NULL;

-- Durable webhook inbox for idempotent provider event processing.
CREATE TABLE billing_webhook_events (
    id                      UUID            PRIMARY KEY,
    provider                VARCHAR(40)     NOT NULL,
    provider_event_id       VARCHAR(200)    NOT NULL,
    event_type              VARCHAR(100)    NOT NULL,
    payload_hash            VARCHAR(64)     NOT NULL,
    received_at             TIMESTAMPTZ     NOT NULL,
    processed_at            TIMESTAMPTZ,
    processing_status       VARCHAR(40)     NOT NULL,
    failure_reason_safe     VARCHAR(500),
    provider_subscription_id VARCHAR(200),
    CONSTRAINT uq_billing_webhook_provider_event
        UNIQUE (provider, provider_event_id),
    CONSTRAINT chk_billing_webhook_status
        CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED'))
);

CREATE INDEX idx_billing_webhook_received_at
    ON billing_webhook_events (received_at);

CREATE INDEX idx_billing_webhook_provider_sub
    ON billing_webhook_events (provider_subscription_id)
    WHERE provider_subscription_id IS NOT NULL;

-- QuoteFlow SaaS revenue reconciliation (NOT tenant invoice payments).
CREATE TABLE billing_transactions (
    id                          UUID            PRIMARY KEY,
    business_id                 UUID            NOT NULL,
    provider                    VARCHAR(40)     NOT NULL,
    provider_payment_id         VARCHAR(200),
    provider_subscription_id    VARCHAR(200),
    plan                        VARCHAR(40)     NOT NULL,
    billing_interval            VARCHAR(20),
    amount_minor                BIGINT,
    currency                    VARCHAR(3),
    status                      VARCHAR(40)     NOT NULL,
    occurred_at                 TIMESTAMPTZ     NOT NULL,
    created_at                  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_billing_tx_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT chk_billing_tx_plan
        CHECK (plan IN ('FREE', 'PRO', 'BUSINESS')),
    CONSTRAINT chk_billing_tx_interval
        CHECK (billing_interval IS NULL OR billing_interval IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT chk_billing_tx_status
        CHECK (status IN ('RECORDED', 'IGNORED'))
);

CREATE UNIQUE INDEX uq_billing_tx_provider_payment
    ON billing_transactions (provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE INDEX idx_billing_tx_business_occurred
    ON billing_transactions (business_id, occurred_at);
