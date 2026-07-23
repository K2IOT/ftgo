package net.ftgo.common.orderflow.replies;

public class CardAuthorizationDenied {

    private Long orderId;
    private String reason;

    public CardAuthorizationDenied() {
    }

    public CardAuthorizationDenied(Long orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
