package net.ftgo.kitchen.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ticket aggregate representing a kitchen-perspective view of an order.
 * 
 * Implements state machine transitions for ticket lifecycle:
 * CREATE_PENDING → AWAITING_ACCEPTANCE → ACCEPTED → PREPARING → READY_FOR_PICKUP → PICKED_UP
 * 
 * Enforces valid state transitions per state machine to ensure consistency.
 */
@Entity
@Table(name = "tickets")
public class Ticket {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Restaurant ID is required")
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;
    
    @NotNull(message = "Order ID is required")
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    
    @NotNull(message = "State is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private TicketState state;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "ticket_id", nullable = false)
    private List<TicketLineItem> lineItems = new ArrayList<>();
    
    @Column(name = "ready_by")
    private LocalDateTime readyBy;
    
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    
    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;
    
    /**
     * Stores the state before a pending operation (cancel/revise) was initiated.
     * Used by undoCancel()/undoRevise() to restore to the correct previous state.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 50)
    private TicketState previousState;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Default constructor for JPA.
     */
    protected Ticket() {
    }
    
    /**
     * Creates a new Ticket in CREATE_PENDING state.
     * 
     * @param restaurantId the restaurant ID
     * @param orderId the order ID
     * @param lineItems the ticket line items
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Ticket(Long restaurantId, Long orderId, List<TicketLineItem> lineItems) {
        validateRestaurantId(restaurantId);
        validateOrderId(orderId);
        validateLineItems(lineItems);
        
        this.restaurantId = restaurantId;
        this.orderId = orderId;
        this.state = TicketState.CREATE_PENDING;
        this.lineItems = new ArrayList<>(lineItems);
        this.createdAt = LocalDateTime.now();
    }
    
    private void validateRestaurantId(Long restaurantId) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("Restaurant ID cannot be null");
        }
    }
    
    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
    }
    
    private void validateLineItems(List<TicketLineItem> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new IllegalArgumentException("Line items cannot be null or empty");
        }
    }
    
    /**
     * Transitions ticket from CREATE_PENDING to AWAITING_ACCEPTANCE.
     * Called when CreateOrderSaga approves the ticket.
     * 
     * @throws IllegalStateException if current state is not CREATE_PENDING
     */
    public void approve() {
        if (state != TicketState.CREATE_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot approve ticket in state %s. Expected CREATE_PENDING.", state)
            );
        }
        this.state = TicketState.AWAITING_ACCEPTANCE;
    }
    
    /**
     * Transitions ticket from AWAITING_ACCEPTANCE to ACCEPTED.
     * Called when kitchen staff accepts the ticket.
     * 
     * @throws IllegalStateException if current state is not AWAITING_ACCEPTANCE
     */
    public void accept() {
        if (state != TicketState.AWAITING_ACCEPTANCE) {
            throw new IllegalStateException(
                String.format("Cannot accept ticket in state %s. Expected AWAITING_ACCEPTANCE.", state)
            );
        }
        this.state = TicketState.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }
    
    /**
     * Transitions ticket from ACCEPTED to PREPARING.
     * Called when kitchen staff begins preparing the order.
     * 
     * @throws IllegalStateException if current state is not ACCEPTED
     */
    public void preparing() {
        if (state != TicketState.ACCEPTED) {
            throw new IllegalStateException(
                String.format("Cannot mark ticket as preparing in state %s. Expected ACCEPTED.", state)
            );
        }
        this.state = TicketState.PREPARING;
    }
    
    /**
     * Transitions ticket from PREPARING to READY_FOR_PICKUP.
     * Called when kitchen staff marks the order as ready.
     * 
     * @throws IllegalStateException if current state is not PREPARING
     */
    public void readyForPickup() {
        if (state != TicketState.PREPARING) {
            throw new IllegalStateException(
                String.format("Cannot mark ticket as ready in state %s. Expected PREPARING.", state)
            );
        }
        this.state = TicketState.READY_FOR_PICKUP;
        this.preparedAt = LocalDateTime.now();
        this.readyBy = LocalDateTime.now();
    }
    
    /**
     * Transitions ticket from READY_FOR_PICKUP to PICKED_UP.
     * Called when courier picks up the order.
     * 
     * @throws IllegalStateException if current state is not READY_FOR_PICKUP
     */
    public void pickedUp() {
        if (state != TicketState.READY_FOR_PICKUP) {
            throw new IllegalStateException(
                String.format("Cannot mark ticket as picked up in state %s. Expected READY_FOR_PICKUP.", state)
            );
        }
        this.state = TicketState.PICKED_UP;
    }
    
    /**
     * Transitions ticket to CANCELLED state.
     * Can be called from any state (compensation logic).
     */
    public void cancel() {
        this.state = TicketState.CANCELLED;
    }
    
    /**
     * Begins cancellation process (for CancelOrderSaga).
     * Sets CANCEL_PENDING semantic lock to prevent concurrent modifications.
     * Stores the previous state for proper undo.
     * 
     * @throws IllegalStateException if ticket is already cancelled or in a pending state
     */
    public void beginCancel() {
        if (state == TicketState.CANCELLED) {
            throw new IllegalStateException("Ticket is already cancelled");
        }
        if (state == TicketState.CANCEL_PENDING || state == TicketState.REVISION_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot cancel ticket in state %s. Operation already in progress.", state)
            );
        }
        this.previousState = this.state;
        this.state = TicketState.CANCEL_PENDING;
    }
    
    /**
     * Confirms cancellation (for CancelOrderSaga).
     * 
     * @throws IllegalStateException if ticket is not in CANCEL_PENDING state
     */
    public void confirmCancel() {
        if (state != TicketState.CANCEL_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot confirm cancel in state %s. Expected CANCEL_PENDING.", state)
            );
        }
        this.state = TicketState.CANCELLED;
        this.previousState = null;
    }
    
    /**
     * Undoes cancellation (compensation for CancelOrderSaga).
     * Restores ticket to its actual previous state (not hardcoded).
     */
    public void undoCancel() {
        if (state == TicketState.CANCEL_PENDING && previousState != null) {
            this.state = previousState;
            this.previousState = null;
        }
    }
    
    /**
     * Begins revision process (for ReviseOrderSaga).
     * Sets REVISION_PENDING semantic lock to prevent concurrent modifications.
     * Stores the previous state for proper undo.
     * 
     * @param revisedLineItems the new line items (validated but not applied until confirmRevise)
     * @throws IllegalStateException if ticket cannot be revised in current state
     */
    public void beginRevise(List<TicketLineItem> revisedLineItems) {
        validateLineItems(revisedLineItems);
        if (state == TicketState.CANCELLED || state == TicketState.PICKED_UP) {
            throw new IllegalStateException(
                String.format("Cannot revise ticket in state %s", state)
            );
        }
        if (state == TicketState.CANCEL_PENDING || state == TicketState.REVISION_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot revise ticket in state %s. Operation already in progress.", state)
            );
        }
        this.previousState = this.state;
        this.state = TicketState.REVISION_PENDING;
    }
    
    /**
     * Confirms revision (for ReviseOrderSaga).
     * Updates line items to the revised version and restores to the previous state.
     * 
     * @param revisedLineItems the new line items
     * @throws IllegalStateException if ticket is not in REVISION_PENDING state
     */
    public void confirmRevise(List<TicketLineItem> revisedLineItems) {
        validateLineItems(revisedLineItems);
        if (state != TicketState.REVISION_PENDING) {
            throw new IllegalStateException(
                String.format("Cannot confirm revise in state %s. Expected REVISION_PENDING.", state)
            );
        }
        this.lineItems.clear();
        this.lineItems.addAll(revisedLineItems);
        // Restore to the state before revision started
        this.state = previousState != null ? previousState : TicketState.AWAITING_ACCEPTANCE;
        this.previousState = null;
    }
    
    /**
     * Undoes revision (compensation for ReviseOrderSaga).
     * Restores ticket to its actual previous state. Line items are not modified
     * because beginRevise() only sets the pending state without applying changes.
     */
    public void undoRevise() {
        if (state == TicketState.REVISION_PENDING && previousState != null) {
            this.state = previousState;
            this.previousState = null;
        }
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public TicketState getState() {
        return state;
    }
    
    public List<TicketLineItem> getLineItems() {
        return List.copyOf(lineItems);
    }
    
    public LocalDateTime getReadyBy() {
        return readyBy;
    }
    
    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }
    
    public LocalDateTime getPreparedAt() {
        return preparedAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    @Override
    public String toString() {
        return String.format("Ticket[id=%d, restaurantId=%d, orderId=%d, state=%s, lineItems=%d]",
            id, restaurantId, orderId, state, lineItems.size());
    }
}
