package net.ftgo.accounting.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import net.ftgo.common.Money;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "payment_reconciliation_cases",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_payment_reconciliation_case_key",
        columnNames = "case_key"
    )
)
public class PaymentReconciliationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_key", nullable = false, length = 255)
    private String caseKey;

    @Column(name = "authorization_id")
    private Long authorizationId;

    @Column(name = "provider_authorization_id", length = 255)
    private String providerAuthorizationId;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 50)
    private PaymentReconciliationCaseType caseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentReconciliationSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentReconciliationCaseStatus status;

    @Column(name = "expected_amount", precision = 10, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "provider_amount", precision = 10, scale = 2)
    private BigDecimal providerAmount;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false)
    private Integer occurrences;

    @Column(name = "first_detected_at", nullable = false, updatable = false)
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt;

    protected PaymentReconciliationCase() {
    }

    public PaymentReconciliationCase(
        String caseKey,
        Long authorizationId,
        String providerAuthorizationId,
        String providerReference,
        PaymentReconciliationCaseType caseType,
        PaymentReconciliationSeverity severity,
        Money expectedAmount,
        Money providerAmount,
        String summary
    ) {
        this.caseKey = requireText(caseKey, "caseKey");
        this.authorizationId = authorizationId;
        this.providerAuthorizationId = providerAuthorizationId;
        this.providerReference = providerReference;
        if (caseType == null) throw new IllegalArgumentException("caseType is required");
        if (severity == null) throw new IllegalArgumentException("severity is required");
        this.caseType = caseType;
        this.severity = severity;
        this.status = PaymentReconciliationCaseStatus.OPEN;
        this.expectedAmount = expectedAmount == null ? null : expectedAmount.getAmount();
        this.providerAmount = providerAmount == null ? null : providerAmount.getAmount();
        this.summary = requireText(summary, "summary");
        this.occurrences = 1;
        this.firstDetectedAt = Instant.now();
        this.lastDetectedAt = this.firstDetectedAt;
    }

    public void seenAgain() {
        occurrences = occurrences == null ? 1 : occurrences + 1;
        lastDetectedAt = Instant.now();
    }

    public void resolve() {
        status = PaymentReconciliationCaseStatus.RESOLVED;
        lastDetectedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) status = PaymentReconciliationCaseStatus.OPEN;
        if (occurrences == null) occurrences = 1;
        if (firstDetectedAt == null) firstDetectedAt = Instant.now();
        if (lastDetectedAt == null) lastDetectedAt = firstDetectedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
        return value;
    }

    public Long getId() { return id; }
    public String getCaseKey() { return caseKey; }
    public Long getAuthorizationId() { return authorizationId; }
    public String getProviderAuthorizationId() { return providerAuthorizationId; }
    public String getProviderReference() { return providerReference; }
    public PaymentReconciliationCaseType getCaseType() { return caseType; }
    public PaymentReconciliationSeverity getSeverity() { return severity; }
    public PaymentReconciliationCaseStatus getStatus() { return status; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public BigDecimal getProviderAmount() { return providerAmount; }
    public String getSummary() { return summary; }
    public Integer getOccurrences() { return occurrences; }
    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public Instant getLastDetectedAt() { return lastDetectedAt; }
}
