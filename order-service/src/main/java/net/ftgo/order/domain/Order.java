package net.ftgo.order.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Address;
import net.ftgo.common.Money;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Aggregate root for the Order bounded context. */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Integer version;

    @NotNull(message = "Order state is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrderState state;

    @NotNull(message = "Consumer ID is required")
    @Column(name = "consumer_id", nullable = false)
    private Long consumerId;

    @NotNull(message = "Restaurant ID is required")
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "pickup_address_street")),
        @AttributeOverride(name = "city", column = @Column(name = "pickup_address_city")),
        @AttributeOverride(name = "state", column = @Column(name = "pickup_address_state")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "pickup_address_zip_code"))
    })
    private Address pickupAddress;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLineItem> lineItems = new ArrayList<>();

    @NotNull(message = "Delivery info is required")
    @Embedded
    private DeliveryInfo deliveryInfo;

    @NotNull(message = "Payment info is required")
    @Embedded
    private PaymentInfo paymentInfo;

    @NotNull(message = "Order total is required")
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(
            name = "amount",
            column = @Column(name = "order_total", nullable = false, precision = 10, scale = 2)
        )
    })
    private Money orderTotal;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "authorization_id")
    private Long authorizationId;

    @Column(name = "credit_reservation_id")
    private Long creditReservationId;

    @Column(name = "acceptance_deadline")
    private LocalDateTime acceptanceDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_state", length = 50)
    private OrderPaymentState paymentState;

    @Column(name = "acceptance_request_id", unique = true, length = 100)
    private String acceptanceRequestId;

    @Column(name = "payment_operation_request_id", unique = true, length = 100)
    private String paymentOperationRequestId;

    @Column(name = "capture_id")
    private Long captureId;

    @Column(name = "payment_failure_code", length = 100)
    private String paymentFailureCode;

    @Column(name = "rejection_code", length = 100)
    private String rejectionCode;

    @Column(name = "rejection_message", length = 500)
    private String rejectionMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Order() {
    }

    public Order(
        Long consumerId,
        Long restaurantId,
        List<OrderLineItem> lineItems,
        DeliveryInfo deliveryInfo,
        PaymentInfo paymentInfo
    ) {
        validateConsumerId(consumerId);
        validateRestaurantId(restaurantId);
        validateLineItems(lineItems);
        validateDeliveryInfo(deliveryInfo);
        validatePaymentInfo(paymentInfo);

        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.deliveryInfo = deliveryInfo;
        this.paymentInfo = paymentInfo;
        this.state = OrderState.APPROVAL_PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        lineItems.forEach(this::addLineItem);
        this.orderTotal = calculateTotal();
    }

    private void validateConsumerId(Long value) {
        if (value == null) throw new IllegalArgumentException("Consumer ID cannot be null");
    }

    private void validateRestaurantId(Long value) {
        if (value == null) throw new IllegalArgumentException("Restaurant ID cannot be null");
    }

    private void validateLineItems(List<OrderLineItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line item");
        }
    }

    private void validateDeliveryInfo(DeliveryInfo value) {
        if (value == null) throw new IllegalArgumentException("Delivery info cannot be null");
    }

    private void validatePaymentInfo(PaymentInfo value) {
        if (value == null) throw new IllegalArgumentException("Payment info cannot be null");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }

    private void addLineItem(OrderLineItem item) {
        item.setOrderId(id);
        lineItems.add(item);
    }

    private Money calculateTotal() {
        return lineItems.stream()
            .map(OrderLineItem::getTotal)
            .reduce(Money.ZERO, Money::add);
    }

    public boolean snapshotPickupAddress(Address address) {
        if (address == null) throw new IllegalArgumentException("Pickup address snapshot is required");
        if (pickupAddress == null) {
            pickupAddress = address;
            touch();
            return true;
        }
        if (pickupAddress.equals(address)) return false;
        throw new IllegalStateException("Pickup address snapshot is immutable");
    }

    public void approve() {
        requireState(OrderState.APPROVAL_PENDING, "approve");
        state = OrderState.APPROVED;
        touch();
    }

    public void reject() {
        requireState(OrderState.APPROVAL_PENDING, "reject");
        state = OrderState.REJECTED;
        touch();
    }

    public boolean awaitRestaurantAcceptance(
        Long ticketId,
        Long authorizationId,
        Long creditReservationId,
        LocalDateTime acceptanceDeadline
    ) {
        requireRemoteResources(ticketId, authorizationId, creditReservationId, acceptanceDeadline);

        if (state == OrderState.AWAITING_RESTAURANT_ACCEPTANCE) {
            if (Objects.equals(this.ticketId, ticketId)
                && Objects.equals(this.authorizationId, authorizationId)
                && Objects.equals(this.creditReservationId, creditReservationId)
                && Objects.equals(this.acceptanceDeadline, acceptanceDeadline)) {
                return false;
            }
            throw new IllegalStateException("Order is already awaiting a different resource set");
        }

        requireState(OrderState.APPROVAL_PENDING, "await restaurant acceptance");
        this.ticketId = ticketId;
        this.authorizationId = authorizationId;
        this.creditReservationId = creditReservationId;
        this.acceptanceDeadline = acceptanceDeadline;
        this.paymentState = OrderPaymentState.AUTHORIZED;
        this.state = OrderState.AWAITING_RESTAURANT_ACCEPTANCE;
        touch();
        return true;
    }

    private void requireRemoteResources(
        Long ticketId,
        Long authorizationId,
        Long creditReservationId,
        LocalDateTime deadline
    ) {
        if (ticketId == null || authorizationId == null || creditReservationId == null || deadline == null) {
            throw new IllegalArgumentException(
                "Ticket, authorization, credit reservation, and acceptance deadline are required");
        }
    }

    /** Claims a restaurant acceptance request and establishes one stable capture key. */
    public boolean beginPaymentCapture(String requestId) {
        requireText(requestId, "Acceptance request ID");
        if (state == OrderState.CONFIRMATION_PENDING
            || state == OrderState.APPROVED
            || paymentState == OrderPaymentState.CAPTURED) {
            if (Objects.equals(acceptanceRequestId, requestId)) return false;
            throw new IllegalStateException("Order already has another acceptance request");
        }
        requireState(OrderState.AWAITING_RESTAURANT_ACCEPTANCE, "begin payment capture");
        if (paymentState != OrderPaymentState.AUTHORIZED) {
            throw new IllegalStateException("Cannot capture payment in financial state " + paymentState);
        }
        this.acceptanceRequestId = requestId;
        this.paymentOperationRequestId = stableCaptureRequestId();
        this.paymentFailureCode = null;
        this.paymentState = OrderPaymentState.CAPTURE_PENDING;
        this.state = OrderState.CONFIRMATION_PENDING;
        touch();
        return true;
    }

    public boolean completePaymentCapture(Long captureId, String requestId) {
        if (captureId == null) throw new IllegalArgumentException("Capture ID is required");
        requireText(requestId, "Capture request ID");
        if (paymentState == OrderPaymentState.CAPTURED) {
            if (Objects.equals(this.captureId, captureId)
                && Objects.equals(paymentOperationRequestId, requestId)) {
                return false;
            }
            throw new IllegalStateException("Payment was already captured by another operation");
        }
        requireState(OrderState.CONFIRMATION_PENDING, "complete payment capture");
        if (paymentState != OrderPaymentState.CAPTURE_PENDING) {
            throw new IllegalStateException("Cannot complete capture in financial state " + paymentState);
        }
        if (!Objects.equals(paymentOperationRequestId, requestId)) {
            throw new IllegalArgumentException("Capture request ID does not match the pending operation");
        }
        this.captureId = captureId;
        this.paymentState = OrderPaymentState.CAPTURED;
        touch();
        return true;
    }

    public boolean failPaymentCapture(String code) {
        String stableCode = code == null || code.isBlank()
            ? "PAYMENT_CAPTURE_FAILED"
            : code;
        if (paymentState == OrderPaymentState.FAILED) {
            if (Objects.equals(paymentFailureCode, stableCode)) return false;
            throw new IllegalStateException("Payment capture already failed with another code");
        }
        if (paymentState == OrderPaymentState.CAPTURED) {
            throw new IllegalStateException("Captured payment cannot regress to failed");
        }
        if (paymentState != OrderPaymentState.CAPTURE_PENDING
            && paymentState != OrderPaymentState.AUTHORIZED) {
            throw new IllegalStateException("Cannot fail capture in financial state " + paymentState);
        }
        this.paymentFailureCode = stableCode;
        this.paymentState = OrderPaymentState.FAILED;
        this.rejectionCode = stableCode;
        this.rejectionMessage = "Payment capture failed: " + stableCode;
        this.state = OrderState.REJECTION_PENDING;
        touch();
        return true;
    }

    private String stableCaptureRequestId() {
        if (id == null || authorizationId == null) {
            throw new IllegalStateException("Persisted order and authorization are required for capture");
        }
        return "capture-order-" + id + "-authorization-" + authorizationId;
    }

    /** Legacy acceptance adapter retained for old tests; production uses beginPaymentCapture. */
    public boolean claimRestaurantAcceptance() {
        if (state == OrderState.CONFIRMATION_PENDING || state == OrderState.APPROVED) return false;
        if (state != OrderState.AWAITING_RESTAURANT_ACCEPTANCE) return false;
        String legacyAcceptanceId = "legacy-accept-order-" + id;
        beginPaymentCapture(legacyAcceptanceId);
        completePaymentCapture(authorizationId, paymentOperationRequestId);
        return true;
    }

    public boolean claimRestaurantRejection(String code, String message) {
        String safeCode = code == null || code.isBlank() ? "RESTAURANT_REJECTED" : code;
        String safeMessage = message == null || message.isBlank()
            ? "The restaurant could not accept this order"
            : message;

        if (state == OrderState.REJECTION_PENDING || state == OrderState.REJECTED) return false;
        if (state != OrderState.AWAITING_RESTAURANT_ACCEPTANCE) return false;

        rejectionCode = safeCode;
        rejectionMessage = safeMessage;
        state = OrderState.REJECTION_PENDING;
        touch();
        return true;
    }

    public boolean confirmRestaurantAcceptance() {
        if (state == OrderState.APPROVED) return false;
        requireState(OrderState.CONFIRMATION_PENDING, "confirm restaurant acceptance");
        if (paymentState != OrderPaymentState.CAPTURED) {
            throw new IllegalStateException("Order cannot be approved before payment capture");
        }
        state = OrderState.APPROVED;
        touch();
        return true;
    }

    public boolean completeRestaurantRejection() {
        if (state == OrderState.REJECTED) return false;
        requireState(OrderState.REJECTION_PENDING, "complete restaurant rejection");
        state = OrderState.REJECTED;
        touch();
        return true;
    }

    public void beginCancel() {
        requireState(OrderState.APPROVED, "cancel");
        state = OrderState.CANCEL_PENDING;
        touch();
    }

    public void confirmCancel() {
        requireState(OrderState.CANCEL_PENDING, "confirm cancel");
        state = OrderState.CANCELLED;
        touch();
    }

    public void undoCancel() {
        requireState(OrderState.CANCEL_PENDING, "undo cancel");
        state = OrderState.APPROVED;
        touch();
    }

    public void beginRevise() {
        requireState(OrderState.APPROVED, "revise");
        state = OrderState.REVISION_PENDING;
        touch();
    }

    public void confirmRevise(List<OrderLineItem> revisedLineItems) {
        requireState(OrderState.REVISION_PENDING, "confirm revise");
        validateLineItems(revisedLineItems);
        lineItems.clear();
        revisedLineItems.forEach(this::addLineItem);
        orderTotal = calculateTotal();
        state = OrderState.APPROVED;
        touch();
    }

    public void undoRevise() {
        requireState(OrderState.REVISION_PENDING, "undo revise");
        state = OrderState.APPROVED;
        touch();
    }

    private void requireState(OrderState expected, String operation) {
        if (state != expected) {
            throw new IllegalStateException(
                "Cannot " + operation + " order in state " + state + ". Expected " + expected + ".");
        }
    }

    public boolean isPending() {
        return state == OrderState.APPROVAL_PENDING
            || state == OrderState.AWAITING_RESTAURANT_ACCEPTANCE
            || state == OrderState.CONFIRMATION_PENDING
            || state == OrderState.REJECTION_PENDING
            || state == OrderState.CANCEL_PENDING
            || state == OrderState.REVISION_PENDING;
    }

    public void validateNotPending() {
        if (isPending()) {
            throw new IllegalStateException(
                "Cannot modify order in state " + state + ". Operation in progress.");
        }
    }

    private void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Integer getVersion() { return version; }
    public OrderState getState() { return state; }
    public Long getConsumerId() { return consumerId; }
    public Long getRestaurantId() { return restaurantId; }
    public Address getPickupAddress() { return pickupAddress; }
    public List<OrderLineItem> getLineItems() { return List.copyOf(lineItems); }
    public DeliveryInfo getDeliveryInfo() { return deliveryInfo; }
    public PaymentInfo getPaymentInfo() { return paymentInfo; }
    public Money getOrderTotal() { return orderTotal; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    public Long getCreditReservationId() { return creditReservationId; }
    public LocalDateTime getAcceptanceDeadline() { return acceptanceDeadline; }
    public OrderPaymentState getPaymentState() { return paymentState; }
    public String getAcceptanceRequestId() { return acceptanceRequestId; }
    public String getPaymentOperationRequestId() { return paymentOperationRequestId; }
    public Long getCaptureId() { return captureId; }
    public String getPaymentFailureCode() { return paymentFailureCode; }
    public String getRejectionCode() { return rejectionCode; }
    public String getRejectionMessage() { return rejectionMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Order{id=" + id
            + ", state=" + state
            + ", paymentState=" + paymentState
            + ", consumerId=" + consumerId
            + ", restaurantId=" + restaurantId
            + ", total=" + orderTotal + "}";
    }
}
