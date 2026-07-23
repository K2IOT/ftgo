package net.ftgo.common.orderflow.replies;

public class PaymentRefunded {

    private Long refundId;
    private Long captureId;
    private Long orderId;

    public PaymentRefunded() {
    }

    public PaymentRefunded(Long refundId, Long captureId, Long orderId) {
        this.refundId = refundId;
        this.captureId = captureId;
        this.orderId = orderId;
    }

    public Long getRefundId() { return refundId; }
    public void setRefundId(Long refundId) { this.refundId = refundId; }
    public Long getCaptureId() { return captureId; }
    public void setCaptureId(Long captureId) { this.captureId = captureId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
