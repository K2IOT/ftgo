package net.ftgo.accounting.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentLedgerEntryRepository extends JpaRepository<PaymentLedgerEntry, Long> {
    Optional<PaymentLedgerEntry> findByRequestId(String requestId);
    List<PaymentLedgerEntry> findByAuthorizationIdOrderByOccurredAtAsc(Long authorizationId);
    List<PaymentLedgerEntry> findByOrderIdOrderByOccurredAtAsc(Long orderId);
}
