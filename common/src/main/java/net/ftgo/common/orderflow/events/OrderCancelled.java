package net.ftgo.common.orderflow.events;

public class OrderCancelled {

    private Long orderId;
    private Long consumerId;
    private Long restaurantId;

    public OrderCancelled() {
    }

    public OrderCancelled(Long orderId, Long consumerId, Long restaurantId) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }
}
