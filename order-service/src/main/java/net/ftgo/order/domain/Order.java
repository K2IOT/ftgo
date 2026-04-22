package net.ftgo.order.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Money;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order aggregate representing a food order in the FTGO system.
 * 
 * The Order aggregate is the root of the order bounded context and manages the complete
 * order lifecycle through a state machine. It implements semantic locking via pending states
 * to prevent concurrent modifications during saga execution.
 * 
 * Optimistic Locking:
 * - Uses version field for optimistic locking to detect concurrent updates
 * - JPA will throw OptimisticLockException if version mismatch occurs
 * 
 * State Machine:
 * - APPROVAL_PENDING → APPROVED/REJECTED (CreateOrderSaga)
 * - APPROVED → CANCEL_PENDING → CANCELLED (CancelOrderSaga)
 * - APPROVED → REVISION_PENDING → APPROVED (ReviseOrderSaga)
 * 
 * Semantic Lock:
 * - Pending states prevent concurrent modifications during saga execution
 * - Operations on pending orders return 409 Conflict error
 */
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
        @AttributeOverride(name = "amount", column = @Column(name = "order_total", nullable = false, precision = 10, scale = 2))
    })
    private Money orderTotal;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Default constructor for JPA.
     */
    protected Order() {
    }
    
    /**
     * Creates a new Order in APPROVAL_PENDING state.
     * 
     * @param consumerId the consumer placing the order
     * @param restaurantId the restaurant fulfilling the order
     * @param lineItems the order line items
     * @param deliveryInfo the delivery information
     * @param paymentInfo the payment information
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Order(Long consumerId, Long restaurantId, List<OrderLineItem> lineItems,
                 DeliveryInfo deliveryInfo, PaymentInfo paymentInfo) {
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
        
        // Add line items and calculate total
        for (OrderLineItem item : lineItems) {
            addLineItem(item);
        }
        
        this.orderTotal = calculateTotal();
    }
    
    /**
     * Validates that the consumer ID is not null.
     * 
     * @param consumerId the consumer ID to validate
     * @throws IllegalArgumentException if consumer ID is null
     */
    private void validateConsumerId(Long consumerId) {
        if (consumerId == null) {
            throw new IllegalArgumentException("Consumer ID cannot be null");
        }
    }
    
    /**
     * Validates that the restaurant ID is not null.
     * 
     * @param restaurantId the restaurant ID to validate
     * @throws IllegalArgumentException if restaurant ID is null
     */
    private void validateRestaurantId(Long restaurantId) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("Restaurant ID cannot be null");
        }
    }
    
    /**
     * Validates that the line items list is not null or empty.
     * 
     * @param lineItems the line items to validate
     * @throws IllegalArgumentException if line items is null or empty
     */
    private void validateLineItems(List<OrderLineItem> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line item");
        }
    }
    
    /**
     * Validates that the delivery info is not null.
     * 
     * @param deliveryInfo the delivery info to validate
     * @throws IllegalArgumentException if delivery info is null
     */
    private void validateDeliveryInfo(DeliveryInfo deliveryInfo) {
        if (deliveryInfo == null) {
            throw new IllegalArgumentException("Delivery info cannot be null");
        }
    }
    
    /**
     * Validates that the payment info is not null.
     * 
     * @param paymentInfo the payment info to validate
     * @throws IllegalArgumentException if payment info is null
     */
    private void validatePaymentInfo(PaymentInfo paymentInfo) {
        if (paymentInfo == null) {
            throw new IllegalArgumentException("Payment info cannot be null");
        }
    }
    
    /**
     * Adds a line item to the order.
     * 
     * @param item the line item to add
     */
    private void addLineItem(OrderLineItem item) {
        item.setOrderId(this.id);
        this.lineItems.add(item);
    }
    
    /**
     * Calculates the total price of all line items.
     * 
     * @return the order total
     */
    private Money calculateTotal() {
        Money total = Money.ZERO;
        for (OrderLineItem item : lineItems) {
            total = total.add(item.getTotal());
        }
        return total;
    }
    
    // State machine transitions
    
    /**
     * Approves the order (APPROVAL_PENDING → APPROVED).
     * Called by CreateOrderSaga when all saga steps succeed.
     * 
     * @throws IllegalStateException if order is not in APPROVAL_PENDING state
     */
    public void approve() {
        if (state != OrderState.APPROVAL_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot approve order in state %s. Expected APPROVAL_PENDING.", state)
            );
        }
        this.state = OrderState.APPROVED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Rejects the order (APPROVAL_PENDING → REJECTED).
     * Called by CreateOrderSaga when saga fails before pivot point.
     * 
     * @throws IllegalStateException if order is not in APPROVAL_PENDING state
     */
    public void reject() {
        if (state != OrderState.APPROVAL_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot reject order in state %s. Expected APPROVAL_PENDING.", state)
            );
        }
        this.state = OrderState.REJECTED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Begins order cancellation (APPROVED → CANCEL_PENDING).
     * Called by CancelOrderSaga at the start of cancellation process.
     * Implements semantic lock to prevent concurrent modifications.
     * 
     * @throws IllegalStateException if order is not in APPROVED state
     */
    public void beginCancel() {
        if (state != OrderState.APPROVED) {
            throw new IllegalStateException(
                String.format("Cannot cancel order in state %s. Expected APPROVED.", state)
            );
        }
        this.state = OrderState.CANCEL_PENDING;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Confirms order cancellation (CANCEL_PENDING → CANCELLED).
     * Called by CancelOrderSaga when cancellation succeeds.
     * 
     * @throws IllegalStateException if order is not in CANCEL_PENDING state
     */
    public void confirmCancel() {
        if (state != OrderState.CANCEL_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot confirm cancel in state %s. Expected CANCEL_PENDING.", state)
            );
        }
        this.state = OrderState.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Undoes order cancellation (CANCEL_PENDING → APPROVED).
     * Called by CancelOrderSaga compensation when cancellation fails.
     * 
     * @throws IllegalStateException if order is not in CANCEL_PENDING state
     */
    public void undoCancel() {
        if (state != OrderState.CANCEL_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot undo cancel in state %s. Expected CANCEL_PENDING.", state)
            );
        }
        this.state = OrderState.APPROVED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Begins order revision (APPROVED → REVISION_PENDING).
     * Called by ReviseOrderSaga at the start of revision process.
     * Implements semantic lock to prevent concurrent modifications.
     * 
     * @throws IllegalStateException if order is not in APPROVED state
     */
    public void beginRevise() {
        if (state != OrderState.APPROVED) {
            throw new IllegalStateException(
                String.format("Cannot revise order in state %s. Expected APPROVED.", state)
            );
        }
        this.state = OrderState.REVISION_PENDING;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Confirms order revision (REVISION_PENDING → APPROVED).
     * Called by ReviseOrderSaga when revision succeeds.
     * Updates line items and recalculates order total.
     * 
     * @param revisedLineItems the new line items
     * @throws IllegalStateException if order is not in REVISION_PENDING state
     * @throws IllegalArgumentException if revised line items are invalid
     */
    public void confirmRevise(List<OrderLineItem> revisedLineItems) {
        if (state != OrderState.REVISION_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot confirm revise in state %s. Expected REVISION_PENDING.", state)
            );
        }
        validateLineItems(revisedLineItems);
        
        // Replace line items
        this.lineItems.clear();
        for (OrderLineItem item : revisedLineItems) {
            addLineItem(item);
        }
        
        // Recalculate total
        this.orderTotal = calculateTotal();
        this.state = OrderState.APPROVED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Undoes order revision (REVISION_PENDING → APPROVED).
     * Called by ReviseOrderSaga compensation when revision fails.
     * 
     * @throws IllegalStateException if order is not in REVISION_PENDING state
     */
    public void undoRevise() {
        if (state != OrderState.REVISION_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot undo revise in state %s. Expected REVISION_PENDING.", state)
            );
        }
        this.state = OrderState.APPROVED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Checks if the order is in a pending state (semantic lock active).
     * 
     * @return true if order is in a pending state, false otherwise
     */
    public boolean isPending() {
        return state == OrderState.APPROVAL_PENDING ||
               state == OrderState.CANCEL_PENDING ||
               state == OrderState.REVISION_PENDING;
    }
    
    /**
     * Validates that the order is not in a pending state.
     * Used to enforce semantic lock and prevent concurrent modifications.
     * 
     * @throws IllegalStateException if order is in a pending state
     */
    public void validateNotPending() {
        if (isPending()) {
            throw new IllegalStateException(
                String.format("Cannot modify order in state %s. Operation in progress.", state)
            );
        }
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public Integer getVersion() {
        return version;
    }
    
    public OrderState getState() {
        return state;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public List<OrderLineItem> getLineItems() {
        return List.copyOf(lineItems);
    }
    
    public DeliveryInfo getDeliveryInfo() {
        return deliveryInfo;
    }
    
    public PaymentInfo getPaymentInfo() {
        return paymentInfo;
    }
    
    public Money getOrderTotal() {
        return orderTotal;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
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
        return String.format("Order{id=%d, state=%s, consumerId=%d, restaurantId=%d, total=%s}", 
            id, state, consumerId, restaurantId, orderTotal);
    }
}
