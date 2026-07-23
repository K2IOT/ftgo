package net.ftgo.common.orderflow.replies;

public class OrderMenuValidationRejected {

    public static final String RESTAURANT_NOT_FOUND = "RESTAURANT_NOT_FOUND";
    public static final String RESTAURANT_CLOSED = "RESTAURANT_CLOSED";
    public static final String MENU_VERSION_CHANGED = "MENU_VERSION_CHANGED";
    public static final String MENU_ITEM_NOT_FOUND = "MENU_ITEM_NOT_FOUND";
    public static final String MENU_ITEM_UNAVAILABLE = "MENU_ITEM_UNAVAILABLE";
    public static final String MENU_PRICE_CHANGED = "MENU_PRICE_CHANGED";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";

    private Long orderId;
    private String reasonCode;
    private String message;

    public OrderMenuValidationRejected() {
    }

    public OrderMenuValidationRejected(Long orderId, String reasonCode, String message) {
        this.orderId = orderId;
        this.reasonCode = reasonCode;
        this.message = message;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
