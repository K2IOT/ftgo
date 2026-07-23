ALTER TABLE tickets
    MODIFY COLUMN state ENUM(
        'CREATE_PENDING',
        'AWAITING_ACCEPTANCE',
        'ACCEPTED',
        'PREPARING',
        'READY_FOR_PICKUP',
        'PICKED_UP',
        'CANCEL_PENDING',
        'REVISION_PENDING',
        'CANCELLED'
    ) NOT NULL;
