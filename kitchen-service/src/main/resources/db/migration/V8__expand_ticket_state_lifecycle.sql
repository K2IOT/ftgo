-- Keep every legacy value while adding the complete Phase 02 restaurant-decision
-- lifecycle. Both columns map to TicketState and must evolve together.
ALTER TABLE tickets
    MODIFY COLUMN state ENUM(
        'CREATE_PENDING',
        'AWAITING_ACCEPTANCE',
        'ACCEPTED',
        'REJECTED_BY_RESTAURANT',
        'REJECTED_TIMEOUT',
        'PREPARING',
        'READY_FOR_PICKUP',
        'PICKED_UP',
        'CANCEL_PENDING',
        'REVISION_PENDING',
        'CANCELLED'
    ) NOT NULL,
    MODIFY COLUMN previous_state ENUM(
        'CREATE_PENDING',
        'AWAITING_ACCEPTANCE',
        'ACCEPTED',
        'REJECTED_BY_RESTAURANT',
        'REJECTED_TIMEOUT',
        'PREPARING',
        'READY_FOR_PICKUP',
        'PICKED_UP',
        'CANCEL_PENDING',
        'REVISION_PENDING',
        'CANCELLED'
    ) NULL;
