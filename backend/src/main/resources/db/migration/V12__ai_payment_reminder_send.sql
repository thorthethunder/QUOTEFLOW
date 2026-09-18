-- QuoteFlow AI Phase 5: payment reminder send proposals (human-approved external email)

ALTER TABLE ai_action_proposals
    DROP CONSTRAINT chk_ai_action_proposals_type;

ALTER TABLE ai_action_proposals
    ADD CONSTRAINT chk_ai_action_proposals_type
    CHECK (action_type IN (
        'QUOTATION_CREATE_DRAFT',
        'INVOICE_CREATE_DRAFT',
        'REMINDER_PREPARE',
        'PAYMENT_REMINDER_SEND'
    ));
