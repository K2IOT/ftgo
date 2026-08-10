package net.ftgo.accounting.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentLedgerEntryRepository extends JpaRepository<PaymentLedgerEntry, Long> {
    Optional<PaymentLedgerEntry> findByRequestId(String requestId);
    List<PaymentLedgerEntry> findByAuthorizationIdOrderByOccurredAtAsc(Long authorizationId);
    List<PaymentLedgerEntry> findByOrderIdOrderByOccurredAtAsc(Long orderId);

    @Query(value = """
        SELECT authorization_id AS authorizationId,
               COALESCE(SUM(CASE WHEN operation_type = 'CAPTURE' THEN amount ELSE 0 END), 0)
                   AS capturedAmount,
               COALESCE(SUM(CASE WHEN operation_type = 'REFUND' THEN amount ELSE 0 END), 0)
                   AS refundedAmount
          FROM payment_ledger_entries
         WHERE authorization_id IN (:authorizationIds)
         GROUP BY authorization_id
        """, nativeQuery = true)
    List<LedgerTotalsProjection> sumSettlementTotalsByAuthorizationIds(
        @Param("authorizationIds") Collection<Long> authorizationIds
    );

    interface LedgerTotalsProjection {
        Long getAuthorizationId();
        BigDecimal getCapturedAmount();
        BigDecimal getRefundedAmount();
    }
}
