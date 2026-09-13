-- QuoteFlow Phase 2: identity and tenant foundation
-- Flyway owns schema. Hibernate validates only (ddl-auto=validate).
-- Immutable once applied to shared environments; later changes use V2+.

CREATE TABLE businesses (
    id                          UUID            PRIMARY KEY,
    name                        VARCHAR(200)    NOT NULL,
    email                       VARCHAR(320),
    phone                       VARCHAR(32),
    address_line1               VARCHAR(200),
    address_line2               VARCHAR(200),
    city                        VARCHAR(100),
    state                       VARCHAR(100),
    postal_code                 VARCHAR(20),
    country_code                VARCHAR(2),
    tax_identification_number   VARCHAR(50),
    currency                    VARCHAR(3)      NOT NULL,
    timezone                    VARCHAR(64)     NOT NULL,
    status                      VARCHAR(20)     NOT NULL,
    created_at                  TIMESTAMPTZ     NOT NULL,
    updated_at                  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT chk_businesses_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT chk_businesses_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_businesses_country_code
        CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$')
);

CREATE TABLE app_users (
    id              UUID            PRIMARY KEY,
    business_id     UUID            NOT NULL,
    email           VARCHAR(320)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    tenant_role     VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    email_verified  BOOLEAN         NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_app_users_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT uq_app_users_email
        UNIQUE (email),
    CONSTRAINT chk_app_users_tenant_role
        CHECK (tenant_role IN ('OWNER', 'ADMIN', 'STAFF')),
    CONSTRAINT chk_app_users_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_app_users_business_id ON app_users (business_id);

CREATE TABLE refresh_tokens (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL,
    token_hash      VARCHAR(128)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL,
    last_used_at    TIMESTAMPTZ,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_token_hash
        UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
