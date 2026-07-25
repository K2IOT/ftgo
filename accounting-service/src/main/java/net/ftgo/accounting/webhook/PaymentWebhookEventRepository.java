package net.ftgo.accounting.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentWebhookEventRepository
    extends JpaRepository<PaymentWebhookEvent, Long> {

    boolean existsByProviderAndProviderEventId(
        String provider,
        String providerEventId
    );
}
