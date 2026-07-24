package net.ftgo.kitchen.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Kitchen-perspective aggregate for an order.
 *
 * <p>The restaurant acceptance decision is guarded by the aggregate state and
 * the database row lock used by application services. Only one of accept,
 * reject, or timeout can transition an awaiting ticket.</p>
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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "ticket_id", nullable = false)
    private List<PendingTicketLineItem> pendingRevisionLineItems = new ArrayList<>();

    @Column(name = "ready_by")
    private LocalDateTime readyBy;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "acceptance_deadline")
    private LocalDateTime acceptanceDeadline;

    @Column(name = "decision_event_id", unique = true, length = 36)
    private String decisionEventId;

    @Column(name = "decision_reason", length = 100)
    private String decisionReason;

    @Column(name = "decision_at")
    private LocalDateTime decisionAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 50)
    private TicketState previousState;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Ticket() {
    }

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

    private void validateRestaurantId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("Restaurant ID cannot be null");
        }
    }

    private void validateOrderId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
    }

    private void validateLineItems(List<TicketLineItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Line items cannot be null or empty");
        }
    }

    /**
     * Backward-compatible approval entrypoint. New saga code should provide an
     * explicit deadline through {@link #approve(LocalDateTime)}.
     */
    public void approve() {
        approve(LocalDateTime.now().plusMinutes(5));
    }

    public void approve(LocalDateTime deadline) {
        if (state != TicketState.CREATE_PENDING) {
            throw new IllegalStateException(
                "Cannot approve ticket in state " + state + ". Expected CREATE_PENDING.");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("Acceptance deadline is required");
        }
        state = TicketState.AWAITING_ACCEPTANCE;
        acceptanceDeadline = deadline;
    }

    /**
     * @return true only when this call wins the acceptance decision.
     */
    public boolean accept() {
        if (state == TicketState.ACCEPTED) {
            return false;
        }
        if (state != TicketState.AWAITING_ACCEPTANCE) {
            throw new IllegalStateException(
                "Cannot accept ticket in state " + state + ". Expected AWAITING_ACCEPTANCE.");
        }
        acceptedAt = LocalDateTime.now();
        state = TicketState.ACCEPTED;
        recordDecision("ACCEPTED", acceptedAt);
        return true;
    }

    /**
     * @return true only when this call wins the acceptance decision.
     */
    public boolean reject(String reason) {
        String stableReason = reason == null || reason.isBlank()
            ? "RESTAURANT_REJECTED"
            : reason;

        if (state == TicketState.REJECTED_BY_RESTAURANT) {
            if (Objects.equals(decisionReason, stableReason)) {
                return false;
            }
            throw new IllegalStateException("Ticket was already rejected for another reason");
        }
        if (state != TicketState.AWAITING_ACCEPTANCE) {
            throw new IllegalStateException(
                "Cannot reject ticket in state " + state + ". Expected AWAITING_ACCEPTANCE.");
        }

        state = TicketState.REJECTED_BY_RESTAURANT;
        recordDecision(stableReason, LocalDateTime.now());
        return true;
    }

    /**
     * @return true only when the deadline has elapsed and this call wins.
     */
    public boolean timeout(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Timeout evaluation time is required");
        }
        if (state != TicketState.AWAITING_ACCEPTANCE) {
            return false;
        }
        if (acceptanceDeadline == null || now.isBefore(acceptanceDeadline)) {
            return false;
        }

        state = TicketState.REJECTED_TIMEOUT;
        recordDecision("ACCEPTANCE_TIMEOUT", now);
        return true;
    }

    private void recordDecision(String reason, LocalDateTime occurredAt) {
        decisionEventId = UUID.randomUUID().toString();
        decisionReason = reason;
        decisionAt = occurredAt;
    }

    public void preparing() {
        if (state != TicketState.ACCEPTED) {
            throw new IllegalStateException(
                "Cannot mark ticket as preparing in state " + state + ". Expected ACCEPTED.");
        }
        state = TicketState.PREPARING;
    }

    public void readyForPickup() {
        if (state != TicketState.PREPARING) {
            throw new IllegalStateException(
                "Cannot mark ticket as ready in state " + state + ". Expected PREPARING.");
        }
        state = TicketState.READY_FOR_PICKUP;
        preparedAt = LocalDateTime.now();
        readyBy = preparedAt;
    }

    public void pickedUp() {
        if (state != TicketState.READY_FOR_PICKUP) {
            throw new IllegalStateException(
                "Cannot mark ticket as picked up in state " + state + ". Expected READY_FOR_PICKUP.");
        }
        state = TicketState.PICKED_UP;
    }

    public void cancel() {
        state = TicketState.CANCELLED;
    }

    public void beginCancel() {
        if (state == TicketState.CANCELLED) {
            throw new IllegalStateException("Ticket is already cancelled");
        }
        if (state == TicketState.CANCEL_PENDING || state == TicketState.REVISION_PENDING) {
            throw new IllegalStateException(
                "Cannot cancel ticket in state " + state + ". Operation already in progress.");
        }
        if (isDecisionRejection()) {
            throw new IllegalStateException("Cannot cancel a rejected ticket");
        }
        if (hasPreparationBegun()) {
            throw new IllegalStateException("Cannot cancel ticket after preparation has begun");
        }
        previousState = state;
        state = TicketState.CANCEL_PENDING;
    }

    private boolean hasPreparationBegun() {
        return state == TicketState.PREPARING
            || state == TicketState.READY_FOR_PICKUP
            || state == TicketState.PICKED_UP;
    }

    private boolean isDecisionRejection() {
        return state == TicketState.REJECTED_BY_RESTAURANT
            || state == TicketState.REJECTED_TIMEOUT;
    }

    public void confirmCancel() {
        if (state != TicketState.CANCEL_PENDING) {
            throw new IllegalStateException(
                "Cannot confirm cancel in state " + state + ". Expected CANCEL_PENDING.");
        }
        state = TicketState.CANCELLED;
        previousState = null;
    }

    public void undoCancel() {
        if (state == TicketState.CANCEL_PENDING && previousState != null) {
            state = previousState;
            previousState = null;
        }
    }

    public void beginRevise(List<TicketLineItem> revisedLineItems) {
        validateLineItems(revisedLineItems);
        if (state == TicketState.CANCELLED || state == TicketState.PICKED_UP || isDecisionRejection()) {
            throw new IllegalStateException("Cannot revise ticket in state " + state);
        }
        if (state == TicketState.CANCEL_PENDING || state == TicketState.REVISION_PENDING) {
            throw new IllegalStateException(
                "Cannot revise ticket in state " + state + ". Operation already in progress.");
        }
        if (hasPreparationBegun()) {
            throw new IllegalStateException("Cannot revise ticket after preparation has begun");
        }
        previousState = state;
        pendingRevisionLineItems.clear();
        pendingRevisionLineItems.addAll(revisedLineItems.stream()
            .map(item -> new PendingTicketLineItem(
                item.getMenuItemId(), item.getName(), item.getQuantity()))
            .toList());
        state = TicketState.REVISION_PENDING;
    }

    public void confirmRevise(List<TicketLineItem> revisedLineItems) {
        validateLineItems(revisedLineItems);
        if (state != TicketState.REVISION_PENDING) {
            throw new IllegalStateException(
                "Cannot confirm revise in state " + state + ". Expected REVISION_PENDING.");
        }
        lineItems.clear();
        lineItems.addAll(revisedLineItems);
        state = previousState != null ? previousState : TicketState.AWAITING_ACCEPTANCE;
        previousState = null;
        pendingRevisionLineItems.clear();
    }

    public void confirmPendingRevise() {
        confirmRevise(pendingRevisionLineItems.stream()
            .map(item -> new TicketLineItem(
                item.getMenuItemId(), item.getName(), item.getQuantity()))
            .toList());
    }

    public void undoRevise() {
        if (state == TicketState.REVISION_PENDING && previousState != null) {
            state = previousState;
            previousState = null;
            pendingRevisionLineItems.clear();
        }
    }

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

    public LocalDateTime getAcceptanceDeadline() {
        return acceptanceDeadline;
    }

    public String getDecisionEventId() {
        return decisionEventId;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public LocalDateTime getDecisionAt() {
        return decisionAt;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (version == null) {
            version = 0L;
        }
    }

    @Override
    public String toString() {
        return "Ticket[id=" + id
            + ", restaurantId=" + restaurantId
            + ", orderId=" + orderId
            + ", state=" + state
            + ", lineItems=" + lineItems.size() + "]";
    }
}
