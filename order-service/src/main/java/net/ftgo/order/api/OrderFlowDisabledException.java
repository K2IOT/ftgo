package net.ftgo.order.api;

/**
 * Raised when operations have disabled new Phase 02 order intake while
 * allowing in-flight sagas and decision consumers to continue draining.
 */
public class OrderFlowDisabledException extends RuntimeException {

    public OrderFlowDisabledException(String message) {
        super(message);
    }
}
