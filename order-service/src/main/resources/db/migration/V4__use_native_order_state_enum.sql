ALTER TABLE orders
    MODIFY COLUMN state ENUM(
        'APPROVAL_PENDING',
        'APPROVED',
        'REJECTED',
        'CANCEL_PENDING',
        'CANCELLED',
        'REVISION_PENDING'
    ) NOT NULL;
