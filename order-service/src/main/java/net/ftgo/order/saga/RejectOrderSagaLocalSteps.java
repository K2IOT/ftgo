package net.ftgo.order.saga;

import net.ftgo.common.orderflow.events.OrderRejected;
import net.ftgo.order.domain.Order;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Final local transaction for restaurant rejection and acceptance timeout.
 */
@Component
public class RejectOrderSagaLocalSteps {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    public RejectOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public boolean rejectOrder(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!order.completeRestaurantRejection()) {
            return false;
        }

        orderRepository.save(order);
        eventPublisher.publishOrderEvent(order.getId(), new OrderRejected(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            order.getRejectionCode() + ":" + order.getRejectionMessage()
        ));
        return true;
    }
}
