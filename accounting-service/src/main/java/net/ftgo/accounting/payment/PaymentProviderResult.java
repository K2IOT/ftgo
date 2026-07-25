package net.ftgo.accounting.payment;

/** Result of a provider-backed capture, void, or refund operation. */
public record PaymentProviderResult(
    boolean successful,
    String providerReference,
    String failureCode
) {

    public PaymentProviderResult {
        if (successful && (providerReference == null || providerReference.isBlank())) {
            throw new IllegalArgumentException("Successful provider result requires a reference");
        }
        if (!successful && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("Failed provider result requires a failure code");
        }
    }

    public static PaymentProviderResult succeeded(String providerReference) {
        return new PaymentProviderResult(true, providerReference, null);
    }

    public static PaymentProviderResult declined(String failureCode) {
        return new PaymentProviderResult(false, null, failureCode);
    }
}
