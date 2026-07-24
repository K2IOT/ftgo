package net.ftgo.common.orderflow.events;

import net.ftgo.common.Address;
import net.ftgo.common.Money;

import java.time.LocalDateTime;

public class OrderApproved {

    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private Money orderTotal;
    private Long ticketId;
    private Long authorizationId;
    private Address pickupAddress;
    private Address deliveryAddress;
    private LocalDateTime deliveryTime;

    public OrderApproved() {
    }

    public OrderApproved(
        Long orderId,
        Long consumerId,
        Long restaurantId,
        Money orderTotal,
        Long ticketId,
        Long authorizationId
    ) {
        this(orderId, consumerId, restaurantId, orderTotal, ticketId, authorizationId,
            null, null, null);
    }

    /**
     * Compatibility constructor for the Phase 01/02 event shape that did not
     * yet carry an immutable pickup-address snapshot.
     */
    public OrderApproved(
        Long orderId,
        Long consumerId,
        Long restaurantId,
        Money orderTotal,
        Long ticketId,
        Long authorizationId,
        Address deliveryAddress,
        LocalDateTime deliveryTime
    ) {
        this(orderId, consumerId, restaurantId, orderTotal, ticketId, authorizationId,
            null, deliveryAddress, deliveryTime);
    }

    public OrderApproved(
        Long orderId,
        Long consumerId,
        Long restaurantId,
        Money orderTotal,
        Long ticketId,
        Long authorizationId,
        Address pickupAddress,
        Address deliveryAddress,
        LocalDateTime deliveryTime
    ) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.orderTotal = orderTotal;
        this.ticketId = ticketId;
        this.authorizationId = authorizationId;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.deliveryTime = deliveryTime;
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

    public Money getOrderTotal() {
        return orderTotal;
    }

    public void setOrderTotal(Money orderTotal) {
        this.orderTotal = orderTotal;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }

    public Address getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(Address pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public Address getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(Address deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(LocalDateTime deliveryTime) {
        this.deliveryTime = deliveryTime;
    }
}
