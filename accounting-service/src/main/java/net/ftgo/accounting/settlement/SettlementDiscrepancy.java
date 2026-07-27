package net.ftgo.accounting.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "settlement_discrepancies")
public class SettlementDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_id", nullable = false)
    private Long authorizationId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "discrepancy_type", nullable = false, length = 48)
    private SettlementDiscrepancyType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private SettlementDiscrepancyStatus status;

    @Column(nullable = false, unique = true, length = 191)
    private String fingerprint;

    @Column(name = "local_state", nullable = false, length = 255)
    private String localState;

    @Column(name = "provider_state", nullable = false, length = 255)
    private String providerState;

    @Column(nullable = false, length = 1000)
    private String details;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "repair_action", length = 48)
    private SettlementRepairAction repairAction;

    @Column(name = "repair_request_id", unique = true, length = 191)
    private String repairRequestId;

    @Column(name = "repair_reason", length = 500)
    private String repairReason;

    @Column(name = "failure_details", length = 1000)
    private String failureDetails;

    @Column(name = "first_detected_at", nullable = false)
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    protected SettlementDiscrepancy() {
    }

    public SettlementDiscrepancy(
        Long authorizationId,
        Long orderId,
        SettlementDiscrepancyType type,
        String fingerprint,
        String localState,
        String providerState,
        String details,
        Instant detectedAt
    ) {
        if (authorizationId == null) throw new IllegalArgumentException("Authorization ID cannot be null");
        if (type == null) throw new IllegalArgumentException("Discrepancy type cannot be null");
        requireText(fingerprint, "Fingerprint");
        requireText(localState, "Local state");
        requireText(providerState, "Provider state");
        requireText(details, "Details");
        if (detectedAt == null) throw new IllegalArgumentException("Detected time cannot be null");
        this.authorizationId = authorizationId;
        this.orderId = orderId;
        this.type = type;
        this.fingerprint = fingerprint;
        this.localState = localState;
        this.providerState = providerState;
        this.details = details;
        this.status = SettlementDiscrepancyStatus.OPEN;
        this.firstDetectedAt = detectedAt;
        this.lastDetectedAt = detectedAt;
        this.updatedAt = detectedAt;
    }

    public boolean refresh(
        String newLocalState,
        String newProviderState,
        String newDetails,
        Instant detectedAt
    ) {
        boolean changed = !Objects.equals(localState, newLocalState)
            || !Objects.equals(providerState, newProviderState)
            || !Objects.equals(details, newDetails)
            || status == SettlementDiscrepancyStatus.RESOLVED;
        localState = newLocalState;
        providerState = newProviderState;
        details = newDetails;
        lastDetectedAt = detectedAt;
        if (status == SettlementDiscrepancyStatus.RESOLVED) {
            status = SettlementDiscrepancyStatus.OPEN;
            resolvedAt = null;
        }
        if (changed) updatedAt = detectedAt;
        return changed;
    }

    public boolean sameRepairRequest(String requestId, SettlementRepairAction action) {
        return Objects.equals(repairRequestId, requestId) && repairAction == action;
    }

    public void acknowledge(
        SettlementRepairAction action,
        String requestId,
        String reason,
        Instant at
    ) {
        prepareRepair(action, requestId, reason, at);
        status = SettlementDiscrepancyStatus.ACKNOWLEDGED;
    }

    public void markRepairing(
        SettlementRepairAction action,
        String requestId,
        String reason,
        Instant at
    ) {
        prepareRepair(action, requestId, reason, at);
        status = SettlementDiscrepancyStatus.REPAIRING;
        failureDetails = null;
    }

    public void resolve(Instant at) {
        status = SettlementDiscrepancyStatus.RESOLVED;
        resolvedAt = at;
        updatedAt = at;
        failureDetails = null;
    }

    public void fail(String failure, Instant at) {
        status = SettlementDiscrepancyStatus.FAILED;
        failureDetails = failure;
        updatedAt = at;
    }

    private void prepareRepair(
        SettlementRepairAction action,
        String requestId,
        String reason,
        Instant at
    ) {
        if (action == null) throw new IllegalArgumentException("Repair action cannot be null");
        requireText(requestId, "Repair request ID");
        if (repairRequestId != null && !repairRequestId.equals(requestId)) {
            throw new IllegalStateException(
                "Discrepancy already has repair request " + repairRequestId
            );
        }
        repairAction = action;
        repairRequestId = requestId;
        repairReason = reason;
        updatedAt = at;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }

    public Long getId() { return id; }
    public Long getAuthorizationId() { return authorizationId; }
    public Long getOrderId() { return orderId; }
    public SettlementDiscrepancyType getType() { return type; }
    public SettlementDiscrepancyStatus getStatus() { return status; }
    public String getFingerprint() { return fingerprint; }
    public String getLocalState() { return localState; }
    public String getProviderState() { return providerState; }
    public String getDetails() { return details; }
    public SettlementRepairAction getRepairAction() { return repairAction; }
    public String getRepairRequestId() { return repairRequestId; }
    public String getRepairReason() { return repairReason; }
    public String getFailureDetails() { return failureDetails; }
    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public Instant getLastDetectedAt() { return lastDetectedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}