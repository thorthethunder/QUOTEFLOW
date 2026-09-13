-- QuoteFlow Phase 7: historical business snapshot on quotations
-- Flyway owns schema. Do not edit V1–V4.
-- Backfill uses current Business profile (pre-V5 rows had no historical seller snapshot).

ALTER TABLE quotations
    ADD COLUMN business_name VARCHAR(200),
    ADD COLUMN business_email VARCHAR(320),
    ADD COLUMN business_phone VARCHAR(32),
    ADD COLUMN business_address_line1 VARCHAR(200),
    ADD COLUMN business_address_line2 VARCHAR(200),
    ADD COLUMN business_city VARCHAR(100),
    ADD COLUMN business_state_region VARCHAR(100),
    ADD COLUMN business_postal_code VARCHAR(20),
    ADD COLUMN business_country_code VARCHAR(2),
    ADD COLUMN business_tax_id VARCHAR(50);

UPDATE quotations q
SET
    business_name = b.name,
    business_email = b.email,
    business_phone = b.phone,
    business_address_line1 = b.address_line1,
    business_address_line2 = b.address_line2,
    business_city = b.city,
    business_state_region = b.state,
    business_postal_code = b.postal_code,
    business_country_code = b.country_code,
    business_tax_id = b.tax_identification_number
FROM businesses b
WHERE q.business_id = b.id
  AND q.business_name IS NULL;

ALTER TABLE quotations
    ALTER COLUMN business_name SET NOT NULL;

ALTER TABLE quotations
    ADD CONSTRAINT chk_quotations_business_country_code
        CHECK (business_country_code IS NULL OR business_country_code ~ '^[A-Z]{2}$');
