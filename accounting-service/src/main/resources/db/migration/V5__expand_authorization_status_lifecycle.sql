-- Keep the legacy values first so existing rows retain their semantic values
-- while Phase 02 adds the full payment authorization lifecycle.
ALTER TABLE authorizations
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'DENIED',
        'REVERSED',
        'AUTHORIZED',
        'CAPTURED',
        'VOIDED',
        'REFUNDED'
    ) NOT NULL;
