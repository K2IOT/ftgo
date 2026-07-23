ALTER TABLE tickets
    MODIFY COLUMN previous_state ENUM(
        'CREATE_PENDING',
        'AWAITING_ACCEPTANCE',
        'ACCEPTED',
        'PREPARING',
        'READY_FOR_PICKUP',
        'PICKED_UP',
        'CANCEL_PENDING',
        'REVISION_PENDING',
        'CANCELLED'
    ) NULL AFTER state;
