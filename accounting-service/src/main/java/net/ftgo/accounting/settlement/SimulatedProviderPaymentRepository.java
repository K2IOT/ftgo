package net.ftgo.accounting.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulatedProviderPaymentRepository
    extends JpaRepository<SimulatedProviderPayment, Long> {

    Optional<SimulatedProviderPayment> findByAuthorizationId(Long authorizationId);
}