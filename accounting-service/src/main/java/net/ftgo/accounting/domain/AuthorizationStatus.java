package net.ftgo.accounting.domain;

/**
 * Enum representing the status of a payment authorization.
 */
public enum AuthorizationStatus {
    /**
     * Authorization was approved successfully.
     */
    APPROVED,
    
    /**
     * Authorization was denied (insufficient funds, invalid card, etc.).
     */
    DENIED,
    
    /**
     * Authorization was reversed (refund/cancellation).
     */
    REVERSED
}
