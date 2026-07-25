package net.ftgo.order.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.TicketAcceptanceRequestedEvent;
import net.ftgo.common.orderflow.events.TicketAcceptanceTimedOutEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderPaymentState;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.CapturePaymentSaga;
import net.ftgo.order.saga.CapturePaymentSagaData;
import net.ftgo.order.saga.RejectOrderSaga;
import net.ftgo.order.saga.RejectOrderSagaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketDecisionEventHandlerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaInstanceFactory sagaInstanceFactory;

    @Mock
    private CapturePaymentSaga capturePaymentSaga;

    @Mock
    private RejectOrderSaga rejectOrderSaga;

    private ObjectMapper objectMapper;
    private TicketDecisionEventHandler handler;
    private Order order;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        handler = new TicketDecisionEventHandler(
            orderRepository,
            sagaInstanceFactory,
            capturePaymentSaga,
            rejectOrderSaga,
            objectMapper
        );
        order = awaitingOrder();
        when(orderRepository.findByIdWithLock(101L)).thenReturn(Optional.of(order));
    }

    @Test
    void acceptanceRequestClaimsDecisionAndStartsCaptureSagaOnce() {
        TicketAcceptanceRequestedEvent event = new TicketAcceptanceRequestedEvent(
            "accept-901",
            901L,
            101L,
            "accept-ticket-901",
            LocalDateTime.now()
        );

        handler.handleAcceptanceRequested(event);
        handler.handleAcceptanceRequested(event);

        assertThat(order.getState()).isEqualTo(OrderState.CONFIRMATION_PENDING);
        assertThat(order.getPaymentState()).isEqualTo(OrderPaymentState.CAPTURE_PENDING);
        ArgumentCaptor<CapturePaymentSagaData> data =
            ArgumentCaptor.forClass(CapturePaymentSagaData.class);
        verify(sagaInstanceFactory, times(1)).create(eq(capturePaymentSaga), data.capture());
        assertThat(data.getValue().getOrderId()).isEqualTo(101L);
        assertThat(data.getValue().getConsumerId()).isEqualTo(301L);
        assertThat(data.getValue().getTicketId()).isEqualTo(901L);
        assertThat(data.getValue().getAuthorizationId()).isEqualTo(501L);
        assertThat(data.getValue().getCreditReservationId()).isEqualTo(601L);
        assertThat(data.getValue().getAcceptanceRequestId()).isEqualTo("accept-ticket-901");
    }

    @Test
    void schemaWrappedAcceptanceRequestStartsCaptureSaga() throws Exception {
        TicketAcceptanceRequestedEvent event = new TicketAcceptanceRequestedEvent(
            "accept-901",
            901L,
            101L,
            "accept-ticket-901",
            LocalDateTime.now()
        );
        String payload = objectMapper.writeValueAsString(Map.of(
            "schema", Map.of("type", "struct"),
            "payload", event
        ));

        handler.handleTicketEvent(payload, "901", "TicketAcceptanceRequestedEvent");

        assertThat(order.getPaymentState()).isEqualTo(OrderPaymentState.CAPTURE_PENDING);
        verify(sagaInstanceFactory).create(
            eq(capturePaymentSaga),
            org.mockito.ArgumentMatchers.any(CapturePaymentSagaData.class)
        );
    }

    @Test
    void rejectionEventClaimsDecisionAndStartsRejectSagaOnce() {
        TicketRejectedEvent event = new TicketRejectedEvent(
            "reject-901",
            901L,
            101L,
            "CAPACITY",
            LocalDateTime.now()
        );

        handler.handleRejected(event);
        handler.handleRejected(event);

        assertThat(order.getState()).isEqualTo(OrderState.REJECTION_PENDING);
        ArgumentCaptor<RejectOrderSagaData> data = ArgumentCaptor.forClass(RejectOrderSagaData.class);
        verify(sagaInstanceFactory, times(1)).create(eq(rejectOrderSaga), data.capture());
        assertThat(data.getValue().getFailureCode()).isEqualTo("CAPACITY");
        assertThat(data.getValue().getAuthorizationId()).isEqualTo(501L);
        assertThat(data.getValue().getCreditReservationId()).isEqualTo(601L);
    }

    @Test
    void timeoutAfterAcceptanceRequestIsAcknowledgedAsStale() {
        handler.handleAcceptanceRequested(new TicketAcceptanceRequestedEvent(
            "accept-901",
            901L,
            101L,
            "accept-ticket-901",
            LocalDateTime.now()
        ));

        handler.handleTimedOut(new TicketAcceptanceTimedOutEvent(
            "timeout-901",
            901L,
            101L,
            LocalDateTime.now().minusSeconds(1),
            LocalDateTime.now()
        ));

        assertThat(order.getState()).isEqualTo(OrderState.CONFIRMATION_PENDING);
        verify(sagaInstanceFactory, never()).create(
            eq(rejectOrderSaga),
            org.mockito.ArgumentMatchers.any(RejectOrderSagaData.class)
        );
    }

    private Order awaitingOrder() {
        Order value = new Order(
            301L,
            202L,
            List.of(new OrderLineItem(11L, "Burger", new Money("25.00"), 1)),
            new DeliveryInfo("1 Main St, Hanoi, HN 10000", LocalDateTime.now().plusHours(1)),
            new PaymentInfo("tok_phase_02")
        );
        ReflectionTestUtils.setField(value, "id", 101L);
        value.awaitRestaurantAcceptance(
            901L,
            501L,
            601L,
            LocalDateTime.now().plusMinutes(5)
        );
        return value;
    }
}
