package net.ftgo.accounting.webhook;

import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private PaymentWebhookEventRepository eventRepository;

    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PaymentWebhookService(authorizationRepository, eventRepository);
    }

    @Test
    void duplicateProviderEventAppliesCaptureExactlyOnce() {
        Authorization authorization = authorized();
        PaymentWebhookPayload payload = new PaymentWebhookPayload(
            "evt_capture_101",
            PaymentWebhookType.PAYMENT_CAPTURED,
            "pa_order_101",
            "pc_order_101",
            null,
            new Money("25.00"),
            Instant.parse("2026-07-25T16:00:00Z")
        );
        when(eventRepository.existsByProviderAndProviderEventId(
            "sandbox", "evt_capture_101"))
            .thenReturn(false, true);
        when(authorizationRepository.findByProviderAuthorizationIdForUpdate("pa_order_101"))
            .thenReturn(Optional.of(authorization));

        PaymentWebhookApplyResult first = service.apply(
            "sandbox", payload, "sha256:payload-101");
        PaymentWebhookApplyResult duplicate = service.apply(
            "sandbox", payload, "sha256:payload-101");

        assertThat(first).isEqualTo(PaymentWebhookApplyResult.APPLIED);
        assertThat(duplicate).isEqualTo(PaymentWebhookApplyResult.DUPLICATE);
        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.CAPTURED);
        assertThat(authorization.getPaymentCaptures()).hasSize(1);
        assertThat(authorization.getSuccessfulCapture().getProviderCaptureId())
            .isEqualTo("pc_order_101");
        verify(authorizationRepository, times(1)).saveAndFlush(authorization);
        verify(eventRepository, times(1)).saveAndFlush(any(PaymentWebhookEvent.class));
    }

    @Test
    void capturedWebhookCannotRegressVoidedAuthorization() {
        Authorization authorization = authorized();
        authorization.voidAuthorization(
            "provider void",
            "void-order-101",
            "pv_order_101"
        );
        PaymentWebhookPayload payload = new PaymentWebhookPayload(
            "evt_late_capture_101",
            PaymentWebhookType.PAYMENT_CAPTURED,
            "pa_order_101",
            "pc_late_101",
            null,
            new Money("25.00"),
            Instant.parse("2026-07-25T16:01:00Z")
        );
        when(eventRepository.existsByProviderAndProviderEventId(
            "sandbox", "evt_late_capture_101"))
            .thenReturn(false);
        when(authorizationRepository.findByProviderAuthorizationIdForUpdate("pa_order_101"))
            .thenReturn(Optional.of(authorization));

        PaymentWebhookApplyResult result = service.apply(
            "sandbox", payload, "sha256:late-capture");

        assertThat(result)
            .isEqualTo(PaymentWebhookApplyResult.IGNORED_STATE_REGRESSION);
        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.VOIDED);
        verify(authorizationRepository, never()).saveAndFlush(authorization);
        ArgumentCaptor<PaymentWebhookEvent> stored =
            ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(eventRepository).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getOutcome())
            .isEqualTo(PaymentWebhookApplyResult.IGNORED_STATE_REGRESSION);
        assertThat(stored.getValue().getPayloadHash())
            .isEqualTo("sha256:late-capture");
        assertThat(stored.getValue().getRawPayload()).isNull();
    }

    @Test
    void stateTransitionAndEventPersistenceShareOneTransactionBoundary() throws Exception {
        Method method = PaymentWebhookService.class.getMethod(
            "apply",
            String.class,
            PaymentWebhookPayload.class,
            String.class
        );

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    private Authorization authorized() {
        return new Authorization(
            101L,
            "authorize-order-101",
            new Money("25.00"),
            "pa_order_101",
            AuthorizationStatus.AUTHORIZED
        );
    }
}
