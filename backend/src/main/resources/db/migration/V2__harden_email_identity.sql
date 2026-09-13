-- Phase 3: enforce canonical email storage (trim + lowercase) at the database.
-- Do not modify V1. Existing unique constraint on email remains.

ALTER TABLE app_users
    ADD CONSTRAINT chk_app_users_email_canonical
        CHECK (email = lower(btrim(email)));
