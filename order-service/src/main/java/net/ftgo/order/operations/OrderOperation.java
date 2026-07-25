package net.ftgo.order.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_operations")
public class OrderOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "operation_type", nullable = false, length = 30)
    private OrderOperationType operationType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private OrderOperationStatus status;

    @Column(name = "classification", length = 30)
    private String classification;

    @Column(name = "requested_action", length = 30)
    private String requestedAction;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OrderOperation() {
    }

    public OrderOperation(
        Long orderId,
        OrderOperationType operationType,
        String idempotencyKey,
        String reason
    ) {
        this.orderId = orderId;
        this.operationType = operationType;
        this.idempotencyKey = idempotencyKey;
        this.reason = reason;
        this.status = OrderOperationStatus.PENDING;
    }

    public void recordAssessment(String classification, String requestedAction, String details) {
        this.classification = classification;
        this.requestedAction = requestedAction;
        this.details = details;
    }

    public void markExecuted(String details) {
        this.status = OrderOperationStatus.EXECUTED;
        this.details = details;
    }

    public void markCompleted(String details) {
        this.status = OrderOperationStatus.COMPLETED;
        this.details = details;
    }

    public void markManualReview(String details) {
        this.status = OrderOperationStatus.MANUAL_REVIEW;
        this.details = details;
    }

    public void markFailed(String details) {
        this.status = OrderOperationStatus.FAILED;
        this.details = details;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public OrderOperationType getOperationType() { return operationType; }
    public OrderOperationStatus getStatus() { return status; }
    public String getClassification() { return classification; }
    public String getRequestedAction() { return requestedAction; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
