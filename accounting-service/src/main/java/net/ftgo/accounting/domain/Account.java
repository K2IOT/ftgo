package net.ftgo.accounting.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import net.ftgo.common.Money;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "consumer_id", nullable = false, unique = true)
    private Long consumerId;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private List<Authorization> authorizations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Account() {
    }

    public Account(Long consumerId) {
        if (consumerId == null) throw new IllegalArgumentException("Consumer ID cannot be null");
        this.consumerId = consumerId;
        this.createdAt = LocalDateTime.now();
    }

    /** Legacy authorization API retained for existing sagas. */
    public Authorization authorize(String requestId, Money amount) {
        Authorization existing = findAuthorizationByRequestId(requestId);
        if (existing != null) return existing;
        Authorization authorization = new Authorization(requestId, amount, AuthorizationStatus.APPROVED);
        authorizations.add(authorization);
        return authorization;
    }

    public Authorization authorize(Long orderId, String requestId, Money amount) {
        return authorize(orderId, requestId, amount, "pa_legacy_" + requestId);
    }

    public Authorization authorize(
        Long orderId,
        String requestId,
        Money amount,
        String providerAuthorizationId
    ) {
        Authorization existing = findAuthorizationByRequestId(requestId);
        if (existing != null) {
            if (!existing.matches(orderId, amount)) {
                throw new IllegalArgumentException(
                    "Authorization request ID was reused with different order data");
            }
            if (!existing.getProviderAuthorizationId().equals(providerAuthorizationId)) {
                throw new IllegalArgumentException(
                    "Authorization request ID was reused with another provider reference");
            }
            return existing;
        }
        Authorization authorization = new Authorization(
            orderId,
            requestId,
            amount,
            providerAuthorizationId,
            AuthorizationStatus.AUTHORIZED
        );
        authorizations.add(authorization);
        return authorization;
    }

    public boolean captureAuthorization(Long orderId, Long authorizationId, String requestId) {
        Authorization authorization = requireAuthorization(authorizationId);
        requireOrder(authorization, orderId);
        return authorization.capture(requestId);
    }

    public boolean voidAuthorization(Long orderId, Long authorizationId, String reason, String requestId) {
        Authorization authorization = requireAuthorization(authorizationId);
        requireOrder(authorization, orderId);
        return authorization.voidAuthorization(reason, requestId);
    }

    public boolean refundPayment(Long orderId, Long authorizationId, Money amount,
                                 String reason, String requestId) {
        Authorization authorization = requireAuthorization(authorizationId);
        requireOrder(authorization, orderId);
        return authorization.refund(amount, reason, requestId);
    }

    public void reverseAuthorization(Long authorizationId) {
        requireAuthorization(authorizationId).reverse();
    }

    public void reverseAuthorizationByRequestId(String requestId) {
        Authorization authorization = findAuthorizationByRequestId(requestId);
        if (authorization == null) {
            throw new IllegalArgumentException(
                "Authorization with request ID " + requestId + " not found");
        }
        authorization.reverse();
    }

    public Authorization reviseAuthorization(Long authorizationId, Money newAmount, String newRequestId) {
        if (newRequestId == null || newRequestId.isBlank()) {
            throw new IllegalArgumentException("New request ID cannot be null or blank");
        }
        Authorization duplicate = findAuthorizationByRequestId(newRequestId);
        if (duplicate != null) return duplicate;

        Authorization existing = requireAuthorization(authorizationId);
        if (existing.isReversed()) throw new IllegalStateException("Cannot revise a reversed authorization");
        if (existing.isDenied()) throw new IllegalStateException("Cannot revise a denied authorization");
        existing.reverse();
        Authorization revised = new Authorization(
            newRequestId,
            newAmount,
            AuthorizationStatus.APPROVED
        );
        authorizations.add(revised);
        return revised;
    }

    public Authorization findAuthorizationByRequestId(String requestId) {
        return authorizations.stream()
            .filter(auth -> auth.getRequestId().equals(requestId))
            .findFirst()
            .orElse(null);
    }

    public Authorization findAuthorizationById(Long authorizationId) {
        return authorizations.stream()
            .filter(auth -> auth.getId() != null && auth.getId().equals(authorizationId))
            .findFirst()
            .orElse(null);
    }

    public Authorization requireAuthorizationById(Long authorizationId) {
        return requireAuthorization(authorizationId);
    }

    private Authorization requireAuthorization(Long authorizationId) {
        Authorization authorization = findAuthorizationById(authorizationId);
        if (authorization == null) {
            throw new IllegalArgumentException(
                "Authorization with ID " + authorizationId + " not found");
        }
        return authorization;
    }

    public void requireOrder(Authorization authorization, Long orderId) {
        if (authorization.getOrderId() == null || !authorization.getOrderId().equals(orderId)) {
            throw new IllegalArgumentException(
                "Authorization does not belong to order " + orderId);
        }
    }

    public List<Authorization> getAuthorizations() { return List.copyOf(authorizations); }
    public Long getId() { return id; }
    public Long getConsumerId() { return consumerId; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @PrePersist
    protected void onCreate() {
        if (version == null) version = 0L;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
