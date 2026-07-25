package net.ftgo.order.operations;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderOperationReconcilerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOperationRepository operationRepository;

    @Mock
    private SagaInstanceInspector sagaInspector;

    @Mock
    private OrderRepairActionExecutor actionExecutor;

    private OrderOperationReconciler reconciler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-25T05:00:00Z"), ZoneOffset.UTC);
        reconciler = new OrderOperationReconciler(
            orderRepository,
            operationRepository,
            sagaInspector,
            actionExecutor,
            new SimpleMeterRegistry(),
            clock
        );
    }

    @Test
    void classifiesMissingCreateSagaAsResumable() {
        Order order = order(OrderState.APPROVAL_PENDING, null, null, null);
        when(sagaInspector.findActiveForOrder(101L)).thenReturn(Optional.empty());

        assertEquals(
            OrderOperationReconciler.Classification.RESUMABLE,
            reconciler.classify(order).classification()
        );
    }

    @Test
    void classifiesDurableRejectionResourcesAsCompensatable() {
        Order order = order(OrderState.REJECTION_PENDING, 201L, 301L, 401L);
        when(sagaInspector.findActiveForOrder(101L)).thenReturn(Optional.empty());

        assertEquals(
            OrderOperationReconciler.Classification.COMPENSATABLE,
            reconciler.classify(order).classification()
        );
    }

    @Test
    void neverAutoForcesAmbiguousRevisionPaymentState() {
        Order order = order(OrderState.REVISION_PENDING, 201L, 301L, 401L);
        when(sagaInspector.findActiveForOrder(101L)).thenReturn(Optional.empty());

        assertEquals(
            OrderOperationReconciler.Classification.MANUAL_REVIEW,
            reconciler.classify(order).classification()
        );
        assertThrows(
            IllegalStateException.class,
            () -> reconciler.repair(
                101L,
                OrderOperationReconciler.RepairAction.MARK_SUCCESS,
                "force revision success",
                "repair-revision-1"
            )
        );
    }

    @Test
    void sameIdempotencyKeyExecutesRepairOnlyOnce() {
        Order order = order(OrderState.APPROVAL_PENDING, null, null, null);
        when(orderRepository.findByIdWithLock(101L)).thenReturn(Optional.of(order));
        when(sagaInspector.findActiveForOrder(101L)).thenReturn(Optional.empty());
        OrderOperation saved = new OrderOperation(
            101L,
            OrderOperationType.CREATE,
            "repair-create-1",
            "operator requested restart"
        );
        when(operationRepository.findByIdempotencyKey("repair-create-1"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(saved));
        when(operationRepository.save(any(OrderOperation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        reconciler.repair(
            101L,
            OrderOperationReconciler.RepairAction.RESTART,
            "operator requested restart",
            "repair-create-1"
        );
        reconciler.repair(
            101L,
            OrderOperationReconciler.RepairAction.RESTART,
            "operator requested restart",
            "repair-create-1"
        );

        verify(actionExecutor, times(1)).restart(order, OrderOperationType.CREATE);
    }

    private Order order(
        OrderState state,
        Long ticketId,
        Long authorizationId,
        Long creditReservationId
    ) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(101L);
        when(order.getState()).thenReturn(state);
        when(order.getTicketId()).thenReturn(ticketId);
        when(order.getAuthorizationId()).thenReturn(authorizationId);
        when(order.getCreditReservationId()).thenReturn(creditReservationId);
        when(order.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 7, 25, 4, 0));
        return order;
    }
}
