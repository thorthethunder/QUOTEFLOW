-- QuoteFlow Phase 8: invoices, invoice items, invoice document sequences
-- Flyway owns schema. Do not edit V1–V5.

ALTER TABLE document_sequences
    DROP CONSTRAINT chk_document_sequences_type;

ALTER TABLE document_sequences
    ADD CONSTRAINT chk_document_sequences_type
        CHECK (document_type IN ('QUOTATION', 'INVOICE'));

CREATE TABLE invoices (
    id                          UUID            PRIMARY KEY,
    business_id                 UUID            NOT NULL,
    customer_id                 UUID            NOT NULL,
    source_quotation_id         UUID,
    invoice_number              VARCHAR(40)     NOT NULL,
    sequence_value              BIGINT          NOT NULL,
    status                      VARCHAR(20)     NOT NULL,
    currency                    VARCHAR(3)      NOT NULL,
    issue_date                  DATE            NOT NULL,
    due_date                    DATE,
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
    business_name               VARCHAR(200)    NOT NULL,
    business_email              VARCHAR(320),
    business_phone              VARCHAR(32),
    business_address_line1      VARCHAR(200),
    business_address_line2      VARCHAR(200),
    business_city               VARCHAR(100),
    business_state_region       VARCHAR(100),
    business_postal_code        VARCHAR(20),
    business_country_code       VARCHAR(2),
    business_tax_id             VARCHAR(50),
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
    CONSTRAINT fk_invoices_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_source_quotation
        FOREIGN KEY (source_quotation_id) REFERENCES quotations (id) ON DELETE RESTRICT,
    CONSTRAINT uq_invoices_business_number
        UNIQUE (business_id, invoice_number),
    CONSTRAINT uq_invoices_source_quotation
        UNIQUE (source_quotation_id),
    CONSTRAINT chk_invoices_status
        CHECK (status IN ('DRAFT', 'SENT', 'CANCELLED')),
    CONSTRAINT chk_invoices_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_invoices_discount_type
        CHECK (discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')),
    CONSTRAINT chk_invoices_amounts_non_negative
        CHECK (
            discount_value >= 0
            AND tax_rate >= 0
            AND tax_rate <= 100
            AND subtotal >= 0
            AND discount_amount >= 0
            AND tax_amount >= 0
            AND total_amount >= 0
        ),
    CONSTRAINT chk_invoices_due_date
        CHECK (due_date IS NULL OR due_date >= issue_date),
    CONSTRAINT chk_invoices_customer_country_code
        CHECK (customer_country_code IS NULL OR customer_country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_invoices_business_country_code
        CHECK (business_country_code IS NULL OR business_country_code ~ '^[A-Z]{2}$')
);

CREATE INDEX idx_invoices_business_id ON invoices (business_id);
CREATE INDEX idx_invoices_business_status ON invoices (business_id, status);
CREATE INDEX idx_invoices_customer_id ON invoices (customer_id);
CREATE INDEX idx_invoices_business_issue_date ON invoices (business_id, issue_date);

CREATE TABLE invoice_items (
    id              UUID            PRIMARY KEY,
    invoice_id      UUID            NOT NULL,
    position        INT             NOT NULL,
    description     VARCHAR(500)    NOT NULL,
    quantity        NUMERIC(19, 4)  NOT NULL,
    unit_price      NUMERIC(19, 4)  NOT NULL,
    line_subtotal   NUMERIC(19, 4)  NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_invoice_items_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT chk_invoice_items_quantity
        CHECK (quantity > 0),
    CONSTRAINT chk_invoice_items_unit_price
        CHECK (unit_price >= 0),
    CONSTRAINT chk_invoice_items_line_subtotal
        CHECK (line_subtotal >= 0),
    CONSTRAINT chk_invoice_items_position
        CHECK (position >= 0),
    CONSTRAINT chk_invoice_items_description
        CHECK (char_length(btrim(description)) > 0)
);

CREATE INDEX idx_invoice_items_invoice_id ON invoice_items (invoice_id);
