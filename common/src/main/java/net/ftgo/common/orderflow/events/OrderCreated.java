package net.ftgo.common.orderflow.events;

import net.ftgo.common.Money;

import java.time.LocalDateTime;
import java.util.List;

public class OrderCreated {

    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private String status;
    private Money orderTotal;
    private List<LineItem> lineItems;
    private String deliveryAddress;
    private LocalDateTime deliveryTime;
    private LocalDateTime createdAt;

    public OrderCreated() {
    }

    public OrderCreated(
        Long orderId,
        Long consumerId,
        Long restaurantId,
        String status,
        Money orderTotal,
        List<LineItem> lineItems,
        String deliveryAddress,
        LocalDateTime deliveryTime,
        LocalDateTime createdAt
    ) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.status = status;
        this.orderTotal = orderTotal;
        this.lineItems = lineItems;
        this.deliveryAddress = deliveryAddress;
        this.deliveryTime = deliveryTime;
        this.createdAt = createdAt;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Money getOrderTotal() {
        return orderTotal;
    }

    public void setOrderTotal(Money orderTotal) {
        this.orderTotal = orderTotal;
    }

    public List<LineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(LocalDateTime deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static class LineItem {
        private Long menuItemId;
        private String name;
        private Money price;
        private Integer quantity;

        public LineItem() {
        }

        public LineItem(Long menuItemId, String name, Money price, Integer quantity) {
            this.menuItemId = menuItemId;
            this.name = name;
            this.price = price;
            this.quantity = quantity;
        }

        public Long getMenuItemId() {
            return menuItemId;
        }

        public void setMenuItemId(Long menuItemId) {
            this.menuItemId = menuItemId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Money getPrice() {
            return price;
        }

        public void setPrice(Money price) {
            this.price = price;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
