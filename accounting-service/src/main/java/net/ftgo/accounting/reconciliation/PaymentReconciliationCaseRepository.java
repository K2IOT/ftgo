package net.ftgo.accounting.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentReconciliationCaseRepository
    extends JpaRepository<PaymentReconciliationCase, Long> {

    Optional<PaymentReconciliationCase> findByCaseKey(String caseKey);

    List<PaymentReconciliationCase> findByStatusOrderByFirstDetectedAtAsc(
        PaymentReconciliationCaseStatus status
    );
}
