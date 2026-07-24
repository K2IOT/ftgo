package net.ftgo.accounting.payment;

/**
 * Result returned by the payment authorization boundary.
 */
public record PaymentAuthorizationDecision(boolean approved, String reason) {

    public static PaymentAuthorizationDecision allow() {
        return new PaymentAuthorizationDecision(true, null);
    }

    public static PaymentAuthorizationDecision denied(String reason) {
        return new PaymentAuthorizationDecision(false, reason);
    }
}
