package net.ftgo.common.orderflow.replies;

public class PaymentCaptured {

    private Long captureId;
    private Long authorizationId;
    private Long orderId;

    public PaymentCaptured() {
    }

    public PaymentCaptured(Long captureId, Long authorizationId, Long orderId) {
        this.captureId = captureId;
        this.authorizationId = authorizationId;
        this.orderId = orderId;
    }

    public Long getCaptureId() { return captureId; }
    public void setCaptureId(Long captureId) { this.captureId = captureId; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
