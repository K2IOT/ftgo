package net.ftgo.accounting.settlement;

/** Stable business validation for refund requests above the captured amount. */
public final class RefundAmountExceedsCapturedException extends IllegalArgumentException {

    public RefundAmountExceedsCapturedException() {
        super("Refund total exceeds captured amount");
    }
}
