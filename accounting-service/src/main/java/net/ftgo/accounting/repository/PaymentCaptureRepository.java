package net.ftgo.accounting.repository;

import net.ftgo.accounting.domain.PaymentCapture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentCaptureRepository extends JpaRepository<PaymentCapture, Long> {

    Optional<PaymentCapture> findByRequestId(String requestId);

    Optional<PaymentCapture> findByProviderCaptureId(String providerCaptureId);
}
