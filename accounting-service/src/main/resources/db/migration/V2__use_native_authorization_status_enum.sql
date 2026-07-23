ALTER TABLE authorizations
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'DENIED',
        'REVERSED'
    ) NOT NULL;
