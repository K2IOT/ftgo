package net.ftgo.accounting.repository;

import net.ftgo.accounting.domain.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    Optional<PaymentRefund> findByRequestId(String requestId);

    Optional<PaymentRefund> findByProviderRefundId(String providerRefundId);
}
