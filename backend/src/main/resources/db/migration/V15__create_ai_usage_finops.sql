CREATE TABLE ai_usage_events (
    id UUID PRIMARY KEY,
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    feature VARCHAR(60) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    provider VARCHAR(60) NOT NULL,
    model VARCHAR(160),
    usage_type VARCHAR(40) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    input_characters INTEGER,
    embedding_count INTEGER,
    retrieved_chunk_count INTEGER,
    estimated_provider_cost NUMERIC(19,8),
    currency VARCHAR(3),
    success BOOLEAN NOT NULL,
    error_category VARCHAR(80),
    occurred_at TIMESTAMPTZ NOT NULL,
    billing_period_key VARCHAR(20) NOT NULL,
    reference_key VARCHAR(160)
);

CREATE INDEX idx_ai_usage_events_business_period_feature
    ON ai_usage_events (business_id, billing_period_key, feature, occurred_at DESC);

CREATE INDEX idx_ai_usage_events_feature_time
    ON ai_usage_events (feature, occurred_at DESC);

CREATE TABLE ai_usage_allowance_buckets (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    feature VARCHAR(60) NOT NULL,
    billing_period_key VARCHAR(20) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ai_usage_allowance_bucket UNIQUE (business_id, feature, billing_period_key),
    CONSTRAINT chk_ai_usage_allowance_used_nonnegative CHECK (used_count >= 0)
);

CREATE INDEX idx_ai_usage_buckets_business_period
    ON ai_usage_allowance_buckets (business_id, billing_period_key);
