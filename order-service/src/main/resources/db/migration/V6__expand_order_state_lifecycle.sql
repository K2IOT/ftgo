-- Preserve every legacy state while adding the Phase 02 restaurant-decision
-- states used by CreateOrderSaga, ConfirmOrderSaga, and RejectOrderSaga.
ALTER TABLE orders
    MODIFY COLUMN state ENUM(
        'APPROVAL_PENDING',
        'APPROVED',
        'REJECTED',
        'CANCEL_PENDING',
        'CANCELLED',
        'REVISION_PENDING',
        'AWAITING_RESTAURANT_ACCEPTANCE',
        'CONFIRMATION_PENDING',
        'REJECTION_PENDING'
    ) NOT NULL;
