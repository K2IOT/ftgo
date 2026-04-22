package net.ftgo.order.domain;

/**
 * Enum representing the state of an Order in its lifecycle.
 * 
 * The state machine enforces valid transitions and implements semantic locking
 * via pending states to prevent concurrent modifications during saga execution.
 * 
 * State Transitions:
 * - APPROVAL_PENDING → APPROVED (via approve())
 * - APPROVAL_PENDING → REJECTED (via reject())
 * - APPROVED → CANCEL_PENDING (via beginCancel())
 * - CANCEL_PENDING → CANCELLED (via confirmCancel())
 * - CANCEL_PENDING → APPROVED (via compensation - undoCancel())
 * - APPROVED → REVISION_PENDING (via beginRevise())
 * - REVISION_PENDING → APPROVED (via confirmRevise())
 * - REVISION_PENDING → APPROVED (via compensation - undoRevise())
 * 
 * Semantic Lock:
 * - APPROVAL_PENDING: Prevents cancel/revise during CreateOrderSaga
 * - CANCEL_PENDING: Prevents revise during CancelOrderSaga
 * - REVISION_PENDING: Prevents cancel/additional revise during ReviseOrderSaga
 */
public enum OrderState {
    /**
     * Order is pending approval during CreateOrderSaga execution.
     * Semantic lock: prevents concurrent cancel/revise operations.
     */
    APPROVAL_PENDING,
    
    /**
     * Order has been approved and is ready for fulfillment.
     * Can transition to CANCEL_PENDING or REVISION_PENDING.
     */
    APPROVED,
    
    /**
     * Order was rejected during CreateOrderSaga (e.g., payment authorization failed).
     * Terminal state.
     */
    REJECTED,
    
    /**
     * Order is pending cancellation during CancelOrderSaga execution.
     * Semantic lock: prevents concurrent revise operations.
     */
    CANCEL_PENDING,
    
    /**
     * Order has been cancelled.
     * Terminal state.
     */
    CANCELLED,
    
    /**
     * Order is pending revision during ReviseOrderSaga execution.
     * Semantic lock: prevents concurrent cancel/revise operations.
     */
    REVISION_PENDING
}
