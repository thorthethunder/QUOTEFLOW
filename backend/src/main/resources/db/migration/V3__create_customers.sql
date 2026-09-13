-- QuoteFlow Phase 5: tenant-owned customers
-- Flyway owns schema. Do not edit V1/V2.

CREATE TABLE customers (
    id              UUID            PRIMARY KEY,
    business_id     UUID            NOT NULL,
    display_name    VARCHAR(200)    NOT NULL,
    email           VARCHAR(320),
    phone           VARCHAR(40),
    company_name    VARCHAR(200),
    address_line1   VARCHAR(200),
    address_line2   VARCHAR(200),
    city            VARCHAR(100),
    state_region    VARCHAR(100),
    postal_code     VARCHAR(20),
    country_code    VARCHAR(2),
    tax_id          VARCHAR(50),
    notes           VARCHAR(2000),
    status          VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_customers_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT chk_customers_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_customers_country_code
        CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_customers_display_name
        CHECK (char_length(btrim(display_name)) > 0)
);

CREATE INDEX idx_customers_business_id ON customers (business_id);
CREATE INDEX idx_customers_business_status ON customers (business_id, status);
