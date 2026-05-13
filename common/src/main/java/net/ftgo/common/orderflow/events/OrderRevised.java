package net.ftgo.common.orderflow.events;

import net.ftgo.common.Money;

import java.util.List;

public class OrderRevised {

    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private List<OrderCreated.LineItem> lineItems;
    private Money orderTotal;

    public OrderRevised() {
    }

    public OrderRevised(
        Long orderId,
        Long consumerId,
        Long restaurantId,
        List<OrderCreated.LineItem> lineItems,
        Money orderTotal
    ) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.lineItems = lineItems;
        this.orderTotal = orderTotal;
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

    public List<OrderCreated.LineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<OrderCreated.LineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public Money getOrderTotal() {
        return orderTotal;
    }

    public void setOrderTotal(Money orderTotal) {
        this.orderTotal = orderTotal;
    }
}
