package net.ftgo.common.orderflow.replies;

public class CardAuthorized {

    private Long authorizationId;
    private Long orderId;

    public CardAuthorized() {
    }

    public CardAuthorized(Long authorizationId) {
        this(authorizationId, null);
    }

    public CardAuthorized(Long authorizationId, Long orderId) {
        this.authorizationId = authorizationId;
        this.orderId = orderId;
    }

    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
