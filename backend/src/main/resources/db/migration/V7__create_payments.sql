-- QuoteFlow Phase 9: payments, receipt sequences
-- Flyway owns schema. Do not edit V1–V6.

ALTER TABLE document_sequences
    DROP CONSTRAINT chk_document_sequences_type;

ALTER TABLE document_sequences
    ADD CONSTRAINT chk_document_sequences_type
        CHECK (document_type IN ('QUOTATION', 'INVOICE', 'RECEIPT'));

CREATE TABLE payments (
    id                              UUID            PRIMARY KEY,
    business_id                     UUID            NOT NULL,
    invoice_id                      UUID            NOT NULL,
    receipt_number                  VARCHAR(40)     NOT NULL,
    receipt_sequence_value          BIGINT          NOT NULL,
    amount                          NUMERIC(19, 4)  NOT NULL,
    currency                        VARCHAR(3)      NOT NULL,
    payment_date                    DATE            NOT NULL,
    payment_method                  VARCHAR(40)     NOT NULL,
    reference                       VARCHAR(200),
    notes                           VARCHAR(2000),
    status                          VARCHAR(20)     NOT NULL,
    invoice_number_snapshot         VARCHAR(40)     NOT NULL,
    invoice_total_at_payment        NUMERIC(19, 4)  NOT NULL,
    previous_paid_amount            NUMERIC(19, 4)  NOT NULL,
    remaining_balance_after_payment NUMERIC(19, 4)  NOT NULL,
    created_by_user_id              UUID,
    voided_at                       TIMESTAMPTZ,
    voided_by_user_id               UUID,
    void_reason                     VARCHAR(500),
    created_at                      TIMESTAMPTZ     NOT NULL,
    updated_at                      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_payments_business
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE RESTRICT,
    CONSTRAINT uq_payments_business_receipt
        UNIQUE (business_id, receipt_number),
    CONSTRAINT chk_payments_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_payments_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payments_method
        CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'UPI_MANUAL', 'CHEQUE', 'OTHER')),
    CONSTRAINT chk_payments_status
        CHECK (status IN ('RECORDED', 'VOIDED')),
    CONSTRAINT chk_payments_money_non_negative
        CHECK (
            invoice_total_at_payment >= 0
            AND previous_paid_amount >= 0
            AND remaining_balance_after_payment >= 0
        ),
    CONSTRAINT chk_payments_void_consistency
        CHECK (
            (status = 'RECORDED' AND voided_at IS NULL AND voided_by_user_id IS NULL AND void_reason IS NULL)
            OR (status = 'VOIDED' AND voided_at IS NOT NULL)
        )
);

CREATE INDEX idx_payments_business_id ON payments (business_id);
CREATE INDEX idx_payments_invoice_id ON payments (invoice_id);
CREATE INDEX idx_payments_invoice_status ON payments (invoice_id, status);
CREATE INDEX idx_payments_payment_date ON payments (business_id, payment_date);
