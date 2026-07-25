package net.ftgo.order.saga;

import io.eventuate.tram.commands.common.Command;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.order.domain.Order;
import net.ftgo.order.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/** Local transactions owned by CapturePaymentSaga. */
@Component
public class CapturePaymentSagaLocalSteps {

    private final OrderRepository orderRepository;
    private final ConfirmOrderSagaLocalSteps confirmOrderSteps;

    public CapturePaymentSagaLocalSteps(
        OrderRepository orderRepository,
        ConfirmOrderSagaLocalSteps confirmOrderSteps
    ) {
        this.orderRepository = orderRepository;
        this.confirmOrderSteps = confirmOrderSteps;
    }

    @Transactional
    public boolean recordPaymentCaptured(Long orderId, Long captureId, String requestId) {
        Order order = requireOrder(orderId);
        boolean changed = order.completePaymentCapture(captureId, requestId);
        if (changed) orderRepository.saveAndFlush(order);
        return changed;
    }

    @Transactional
    public boolean completeOrder(Long orderId) {
        return confirmOrderSteps.confirmOrder(orderId);
    }

    @Transactional
    public Message failPaymentCapture(
        CommandMessage<FailPaymentCaptureCommand> message
    ) {
        try {
            FailPaymentCaptureCommand command = message.getCommand();
            Order order = requireOrder(command.getOrderId());
            boolean changed = order.failPaymentCapture(command.getFailureCode());
            if (order.getState() == net.ftgo.order.domain.OrderState.REJECTION_PENDING) {
                order.completeRestaurantRejection();
                changed = true;
            }
            if (changed) orderRepository.saveAndFlush(order);
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Order requireOrder(Long orderId) {
        return orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    public static class FailPaymentCaptureCommand implements Command {

        private Long orderId;
        private String failureCode;

        public FailPaymentCaptureCommand() {
        }

        public FailPaymentCaptureCommand(Long orderId, String failureCode) {
            this.orderId = orderId;
            this.failureCode = failureCode;
        }

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getFailureCode() { return failureCode; }
        public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    }
}
