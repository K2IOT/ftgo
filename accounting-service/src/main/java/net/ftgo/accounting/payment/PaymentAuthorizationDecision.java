package net.ftgo.accounting.payment;

/** Result returned by the payment authorization boundary. */
public record PaymentAuthorizationDecision(
    boolean approved,
    String reason,
    String providerAuthorizationId
) {

    public PaymentAuthorizationDecision(boolean approved, String reason) {
        this(approved, reason, null);
    }

    public static PaymentAuthorizationDecision allow() {
        return new PaymentAuthorizationDecision(true, null, null);
    }

    public static PaymentAuthorizationDecision allow(String providerAuthorizationId) {
        if (providerAuthorizationId == null || providerAuthorizationId.isBlank()) {
            throw new IllegalArgumentException("Provider authorization ID is required");
        }
        return new PaymentAuthorizationDecision(true, null, providerAuthorizationId);
    }

    public static PaymentAuthorizationDecision denied(String reason) {
        return new PaymentAuthorizationDecision(false, reason, null);
    }
}
