package net.ftgo.order.saga;

import net.ftgo.common.Money;
import net.ftgo.common.orderflow.menu.OrderMenuLineItem;
import net.ftgo.order.domain.OrderLineItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable state for the compensatable order-creation phase.
 */
public class CreateOrderSagaData {

    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private List<OrderLineItem> lineItems;
    private Money orderTotal;

    private Long expectedMenuVersion = 0L;
    private List<OrderMenuLineItem> requestedMenuItems;
    private List<OrderMenuLineItem> authoritativeMenuItems;
    private Money authoritativeTotal;

    private Long creditReservationId;
    private Long ticketId;
    private Long authorizationId;
    private LocalDateTime acceptanceDeadline;

    private String failureCode;
    private String failureMessage;

    public CreateOrderSagaData() {
    }

    /**
     * Backward-compatible constructor used by OrderService and existing tests.
     */
    public CreateOrderSagaData(
        Long orderId,
        Long consumerId,
        Long restaurantId,
        List<OrderLineItem> lineItems,
        Money orderTotal
    ) {
        this(orderId, consumerId, restaurantId, lineItems, orderTotal, 0L);
    }

    public CreateOrderSagaData(
        Long orderId,
        Long consumerId,
        Long restaurantId,
        List<OrderLineItem> lineItems,
        Money orderTotal,
        Long expectedMenuVersion
    ) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.lineItems = lineItems == null ? new ArrayList<>() : new ArrayList<>(lineItems);
        this.orderTotal = orderTotal;
        this.expectedMenuVersion = expectedMenuVersion == null ? 0L : expectedMenuVersion;
        this.requestedMenuItems = this.lineItems.stream()
            .map(item -> new OrderMenuLineItem(
                item.getMenuItemId(),
                item.getName(),
                item.getPrice(),
                item.getQuantity()
            ))
            .toList();
        this.authoritativeMenuItems = new ArrayList<>();
        this.acceptanceDeadline = LocalDateTime.now().plusMinutes(5);
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public List<OrderLineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<OrderLineItem> lineItems) {
        this.lineItems = lineItems == null ? new ArrayList<>() : new ArrayList<>(lineItems);
    }
    public Money getOrderTotal() { return authoritativeTotal != null ? authoritativeTotal : orderTotal; }
    public void setOrderTotal(Money orderTotal) { this.orderTotal = orderTotal; }
    public Long getExpectedMenuVersion() { return expectedMenuVersion; }
    public void setExpectedMenuVersion(Long expectedMenuVersion) { this.expectedMenuVersion = expectedMenuVersion; }
    public List<OrderMenuLineItem> getRequestedMenuItems() { return requestedMenuItems; }
    public void setRequestedMenuItems(List<OrderMenuLineItem> requestedMenuItems) {
        this.requestedMenuItems = requestedMenuItems == null
            ? new ArrayList<>()
            : new ArrayList<>(requestedMenuItems);
    }
    public List<OrderMenuLineItem> getAuthoritativeMenuItems() { return authoritativeMenuItems; }
    public void setAuthoritativeMenuItems(List<OrderMenuLineItem> authoritativeMenuItems) {
        this.authoritativeMenuItems = authoritativeMenuItems == null
            ? new ArrayList<>()
            : new ArrayList<>(authoritativeMenuItems);
    }
    public Money getAuthoritativeTotal() { return authoritativeTotal; }
    public void setAuthoritativeTotal(Money authoritativeTotal) { this.authoritativeTotal = authoritativeTotal; }
    public Long getCreditReservationId() { return creditReservationId; }
    public void setCreditReservationId(Long creditReservationId) { this.creditReservationId = creditReservationId; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    public LocalDateTime getAcceptanceDeadline() { return acceptanceDeadline; }
    public void setAcceptanceDeadline(LocalDateTime acceptanceDeadline) { this.acceptanceDeadline = acceptanceDeadline; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    @Override
    public String toString() {
        return "CreateOrderSagaData{orderId=" + orderId
            + ", consumerId=" + consumerId
            + ", restaurantId=" + restaurantId
            + ", total=" + getOrderTotal()
            + ", creditReservationId=" + creditReservationId
            + ", ticketId=" + ticketId
            + ", authorizationId=" + authorizationId + "}";
    }
}
