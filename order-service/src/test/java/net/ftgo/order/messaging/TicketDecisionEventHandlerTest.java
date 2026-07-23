package net.ftgo.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.TicketAcceptanceTimedOutEvent;
import net.ftgo.common.orderflow.events.TicketAcceptedEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.ConfirmOrderSaga;
import net.ftgo.order.saga.ConfirmOrderSagaData;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private ConfirmOrderSaga confirmOrderSaga;

    @Mock
    private RejectOrderSaga rejectOrderSaga;

    private TicketDecisionEventHandler handler;
    private Order order;

    @BeforeEach
    void setUp() {
        handler = new TicketDecisionEventHandler(
            orderRepository,
            sagaInstanceFactory,
            confirmOrderSaga,
            rejectOrderSaga,
            new ObjectMapper().findAndRegisterModules()
        );
        order = awaitingOrder();
        when(orderRepository.findByIdWithLock(101L)).thenReturn(Optional.of(order));
    }

    @Test
    void acceptedEventClaimsDecisionAndStartsConfirmSagaOnce() {
        TicketAcceptedEvent event = new TicketAcceptedEvent(
            "accept-901",
            901L,
            101L,
            LocalDateTime.now()
        );

        handler.handleAccepted(event);
        handler.handleAccepted(event);

        assertThat(order.getState()).isEqualTo(OrderState.CONFIRMATION_PENDING);
        ArgumentCaptor<ConfirmOrderSagaData> data = ArgumentCaptor.forClass(ConfirmOrderSagaData.class);
        verify(sagaInstanceFactory, times(1)).create(confirmOrderSaga, data.capture());
        assertThat(data.getValue().getOrderId()).isEqualTo(101L);
        assertThat(data.getValue().getConsumerId()).isEqualTo(301L);
        assertThat(data.getValue().getAuthorizationId()).isEqualTo(501L);
        assertThat(data.getValue().getCreditReservationId()).isEqualTo(601L);
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
        verify(sagaInstanceFactory, times(1)).create(rejectOrderSaga, data.capture());
        assertThat(data.getValue().getFailureCode()).isEqualTo("CAPACITY");
        assertThat(data.getValue().getAuthorizationId()).isEqualTo(501L);
        assertThat(data.getValue().getCreditReservationId()).isEqualTo(601L);
    }

    @Test
    void timeoutAfterAcceptanceIsAcknowledgedAsStale() {
        handler.handleAccepted(new TicketAcceptedEvent(
            "accept-901",
            901L,
            101L,
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
            org.mockito.ArgumentMatchers.eq(rejectOrderSaga),
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
