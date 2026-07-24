package net.ftgo.common.orderflow.replies;

import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.menu.OrderMenuLineItem;

import java.util.List;

public class OrderMenuValidated {

    private Long orderId;
    private Long restaurantId;
    private Long currentMenuVersion;
    private Address pickupAddress;
    private List<OrderMenuLineItem> authoritativeLineItems;
    private Money authoritativeTotal;

    public OrderMenuValidated() {
    }

    public OrderMenuValidated(Long orderId, Long restaurantId, Long currentMenuVersion,
                              List<OrderMenuLineItem> authoritativeLineItems, Money authoritativeTotal) {
        this(orderId, restaurantId, currentMenuVersion, null, authoritativeLineItems, authoritativeTotal);
    }

    public OrderMenuValidated(Long orderId, Long restaurantId, Long currentMenuVersion,
                              Address pickupAddress, List<OrderMenuLineItem> authoritativeLineItems,
                              Money authoritativeTotal) {
        this.orderId = orderId;
        this.restaurantId = restaurantId;
        this.currentMenuVersion = currentMenuVersion;
        this.pickupAddress = pickupAddress;
        this.authoritativeLineItems = authoritativeLineItems;
        this.authoritativeTotal = authoritativeTotal;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public Long getCurrentMenuVersion() { return currentMenuVersion; }
    public void setCurrentMenuVersion(Long currentMenuVersion) { this.currentMenuVersion = currentMenuVersion; }
    public Address getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(Address pickupAddress) { this.pickupAddress = pickupAddress; }
    public List<OrderMenuLineItem> getAuthoritativeLineItems() { return authoritativeLineItems; }
    public void setAuthoritativeLineItems(List<OrderMenuLineItem> authoritativeLineItems) { this.authoritativeLineItems = authoritativeLineItems; }
    public Money getAuthoritativeTotal() { return authoritativeTotal; }
    public void setAuthoritativeTotal(Money authoritativeTotal) { this.authoritativeTotal = authoritativeTotal; }
}
