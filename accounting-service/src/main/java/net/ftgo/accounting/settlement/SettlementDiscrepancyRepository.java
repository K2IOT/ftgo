package net.ftgo.accounting.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementDiscrepancyRepository
    extends JpaRepository<SettlementDiscrepancy, Long> {

    Optional<SettlementDiscrepancy> findByFingerprint(String fingerprint);

    List<SettlementDiscrepancy> findByStatusIn(
        Collection<SettlementDiscrepancyStatus> statuses
    );

    List<SettlementDiscrepancy> findByAuthorizationIdIn(
        Collection<Long> authorizationIds
    );

    List<SettlementDiscrepancy> findByAuthorizationIdOrderByFirstDetectedAtDesc(
        Long authorizationId
    );

    List<SettlementDiscrepancy> findAllByOrderByFirstDetectedAtDesc();
}
