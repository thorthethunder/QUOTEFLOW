-- QuoteFlow Phase 11: platform SaaS subscriptions + document branding snapshots
-- Flyway owns schema. Do not edit V1–V7.
-- Tenant invoice/payment domain remains separate from QuoteFlow platform billing.

CREATE TABLE subscriptions (
    id                          UUID            PRIMARY KEY,
    business_id                 UUID            NOT NULL,
    plan                        VARCHAR(40)     NOT NULL,
    status                      VARCHAR(20)     NOT NULL,
    current_period_start        TIMESTAMPTZ,
    current_period_end          TIMESTAMPTZ,
    provider                    VARCHAR(40),
    provider_customer_id        VARCHAR(200),
    provider_subscription_id    VARCHAR(200),
    cancel_at_period_end        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ     NOT NULL,
    updated_at                  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_subscriptions_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT uq_subscriptions_business
        UNIQUE (business_id),
    CONSTRAINT chk_subscriptions_plan
        CHECK (plan IN ('FREE', 'PRO', 'BUSINESS')),
    CONSTRAINT chk_subscriptions_status
        CHECK (status IN ('ACTIVE', 'CANCELLED', 'TRIALING', 'PAST_DUE'))
);

CREATE INDEX idx_subscriptions_plan ON subscriptions (plan);

-- Backfill every existing business onto FREE / ACTIVE (Phase 11 default).
INSERT INTO subscriptions (
    id, business_id, plan, status,
    current_period_start, current_period_end,
    provider, provider_customer_id, provider_subscription_id,
    cancel_at_period_end, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    b.id,
    'FREE',
    'ACTIVE',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    FALSE,
    NOW(),
    NOW()
FROM businesses b
WHERE NOT EXISTS (
    SELECT 1 FROM subscriptions s WHERE s.business_id = b.id
);

-- Historical PDF branding entitlement snapshot (frozen at SEND for quotations).
ALTER TABLE quotations
    ADD COLUMN show_quoteflow_branding BOOLEAN NOT NULL DEFAULT TRUE;

-- Receipt branding snapshot (frozen at payment record time).
ALTER TABLE payments
    ADD COLUMN show_quoteflow_branding BOOLEAN NOT NULL DEFAULT TRUE;

-- Monthly quota counts use created_at (half-open Instant range in Business timezone).
CREATE INDEX idx_quotations_business_created_at ON quotations (business_id, created_at);
CREATE INDEX idx_invoices_business_created_at ON invoices (business_id, created_at);
