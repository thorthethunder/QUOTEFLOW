-- QuoteFlow Phase 6: quotations, items, tenant document sequences
-- Flyway owns schema. Do not edit V1–V3.

CREATE TABLE document_sequences (
    business_id     UUID            NOT NULL,
    document_type   VARCHAR(40)     NOT NULL,
    next_value      BIGINT          NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_document_sequences PRIMARY KEY (business_id, document_type),
    CONSTRAINT fk_document_sequences_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT chk_document_sequences_type
        CHECK (document_type IN ('QUOTATION')),
    CONSTRAINT chk_document_sequences_next_value
        CHECK (next_value >= 1)
);

CREATE TABLE quotations (
    id                          UUID            PRIMARY KEY,
    business_id                 UUID            NOT NULL,
    customer_id                 UUID            NOT NULL,
    quotation_number            VARCHAR(40)     NOT NULL,
    sequence_value              BIGINT          NOT NULL,
    status                      VARCHAR(20)     NOT NULL,
    currency                    VARCHAR(3)      NOT NULL,
    issue_date                  DATE            NOT NULL,
    valid_until                 DATE,
    customer_display_name       VARCHAR(200)    NOT NULL,
    customer_company_name       VARCHAR(200),
    customer_email              VARCHAR(320),
    customer_phone              VARCHAR(40),
    customer_address_line1      VARCHAR(200),
    customer_address_line2      VARCHAR(200),
    customer_city               VARCHAR(100),
    customer_state_region       VARCHAR(100),
    customer_postal_code        VARCHAR(20),
    customer_country_code       VARCHAR(2),
    customer_tax_id             VARCHAR(50),
    notes                       VARCHAR(4000),
    terms                       VARCHAR(4000),
    discount_type               VARCHAR(20)     NOT NULL,
    discount_value              NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    tax_rate                    NUMERIC(9, 4)   NOT NULL DEFAULT 0,
    subtotal                    NUMERIC(19, 4)  NOT NULL,
    discount_amount             NUMERIC(19, 4)  NOT NULL,
    tax_amount                  NUMERIC(19, 4)  NOT NULL,
    total_amount                NUMERIC(19, 4)  NOT NULL,
    version                     BIGINT          NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ     NOT NULL,
    updated_at                  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_quotations_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quotations_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT uq_quotations_business_number
        UNIQUE (business_id, quotation_number),
    CONSTRAINT chk_quotations_status
        CHECK (status IN ('DRAFT', 'SENT', 'CANCELLED')),
    CONSTRAINT chk_quotations_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_quotations_discount_type
        CHECK (discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')),
    CONSTRAINT chk_quotations_amounts_non_negative
        CHECK (
            discount_value >= 0
            AND tax_rate >= 0
            AND tax_rate <= 100
            AND subtotal >= 0
            AND discount_amount >= 0
            AND tax_amount >= 0
            AND total_amount >= 0
        ),
    CONSTRAINT chk_quotations_valid_until
        CHECK (valid_until IS NULL OR valid_until >= issue_date),
    CONSTRAINT chk_quotations_country_code
        CHECK (customer_country_code IS NULL OR customer_country_code ~ '^[A-Z]{2}$')
);

CREATE INDEX idx_quotations_business_id ON quotations (business_id);
CREATE INDEX idx_quotations_business_status ON quotations (business_id, status);
CREATE INDEX idx_quotations_customer_id ON quotations (customer_id);
CREATE INDEX idx_quotations_business_issue_date ON quotations (business_id, issue_date);

CREATE TABLE quotation_items (
    id              UUID            PRIMARY KEY,
    quotation_id    UUID            NOT NULL,
    position        INT             NOT NULL,
    description     VARCHAR(500)    NOT NULL,
    quantity        NUMERIC(19, 4)  NOT NULL,
    unit_price      NUMERIC(19, 4)  NOT NULL,
    line_subtotal   NUMERIC(19, 4)  NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_quotation_items_quotation
        FOREIGN KEY (quotation_id) REFERENCES quotations (id) ON DELETE CASCADE,
    CONSTRAINT chk_quotation_items_quantity
        CHECK (quantity > 0),
    CONSTRAINT chk_quotation_items_unit_price
        CHECK (unit_price >= 0),
    CONSTRAINT chk_quotation_items_line_subtotal
        CHECK (line_subtotal >= 0),
    CONSTRAINT chk_quotation_items_position
        CHECK (position >= 0),
    CONSTRAINT chk_quotation_items_description
        CHECK (char_length(btrim(description)) > 0)
);

CREATE INDEX idx_quotation_items_quotation_id ON quotation_items (quotation_id);
