package net.ftgo.order.operations;

import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import net.ftgo.order.domain.Order;
import net.ftgo.order.saga.CancelOrderSaga;
import net.ftgo.order.saga.CancelOrderSagaData;
import net.ftgo.order.saga.ConfirmOrderSaga;
import net.ftgo.order.saga.ConfirmOrderSagaData;
import net.ftgo.order.saga.CreateOrderSaga;
import net.ftgo.order.saga.CreateOrderSagaData;
import net.ftgo.order.saga.RejectOrderSaga;
import net.ftgo.order.saga.RejectOrderSagaData;
import org.springframework.stereotype.Component;

@Component
public class EventuateOrderRepairActionExecutor implements OrderRepairActionExecutor {

    private final SagaInstanceFactory sagaInstanceFactory;
    private final CreateOrderSaga createOrderSaga;
    private final ConfirmOrderSaga confirmOrderSaga;
    private final RejectOrderSaga rejectOrderSaga;
    private final CancelOrderSaga cancelOrderSaga;

    public EventuateOrderRepairActionExecutor(
        SagaInstanceFactory sagaInstanceFactory,
        CreateOrderSaga createOrderSaga,
        ConfirmOrderSaga confirmOrderSaga,
        RejectOrderSaga rejectOrderSaga,
        CancelOrderSaga cancelOrderSaga
    ) {
        this.sagaInstanceFactory = sagaInstanceFactory;
        this.createOrderSaga = createOrderSaga;
        this.confirmOrderSaga = confirmOrderSaga;
        this.rejectOrderSaga = rejectOrderSaga;
        this.cancelOrderSaga = cancelOrderSaga;
    }

    @Override
    public void restart(Order order, OrderOperationType operationType) {
        switch (operationType) {
            case CREATE -> sagaInstanceFactory.create(createOrderSaga, new CreateOrderSagaData(
                order.getId(),
                order.getConsumerId(),
                order.getRestaurantId(),
                order.getLineItems(),
                order.getOrderTotal(),
                0L,
                order.getPaymentInfo().getPaymentToken()
            ));
            case CONFIRM -> sagaInstanceFactory.create(confirmOrderSaga, new ConfirmOrderSagaData(
                order.getId(),
                order.getConsumerId(),
                require(order.getAuthorizationId(), "authorizationId"),
                require(order.getCreditReservationId(), "creditReservationId")
            ));
            case REJECT -> startReject(order);
            case CANCEL -> sagaInstanceFactory.create(cancelOrderSaga, new CancelOrderSagaData(
                order.getId(),
                order.getConsumerId(),
                require(order.getTicketId(), "ticketId"),
                require(order.getAuthorizationId(), "authorizationId")
            ));
            case REVISE -> throw new IllegalStateException(
                "Revision saga cannot be reconstructed without the original revised line items"
            );
        }
    }

    @Override
    public void compensate(Order order, OrderOperationType operationType) {
        if (operationType != OrderOperationType.REJECT) {
            throw new IllegalStateException(
                "Automatic compensation is supported only for a durable rejection operation"
            );
        }
        startReject(order);
    }

    private void startReject(Order order) {
        sagaInstanceFactory.create(rejectOrderSaga, new RejectOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            require(order.getAuthorizationId(), "authorizationId"),
            require(order.getCreditReservationId(), "creditReservationId"),
            order.getRejectionCode() == null ? "OPERATOR_RECONCILIATION" : order.getRejectionCode(),
            order.getRejectionMessage() == null
                ? "Resumed durable rejection compensation"
                : order.getRejectionMessage()
        ));
    }

    private Long require(Long value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " is required for saga repair");
        }
        return value;
    }
}
