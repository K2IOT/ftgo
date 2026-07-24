package net.ftgo.order.saga;

import net.ftgo.common.Address;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.order.domain.Order;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Final local transaction for the post-acceptance confirmation saga. */
@Component
public class ConfirmOrderSagaLocalSteps {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    public ConfirmOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public boolean confirmOrder(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!order.confirmRestaurantAcceptance()) {
            return false;
        }
        if (order.getPickupAddress() == null) {
            throw new IllegalStateException(
                "Order " + orderId + " cannot be approved without a pickup address snapshot"
            );
        }

        orderRepository.saveAndFlush(order);
        eventPublisher.publishOrderEvent(
            order.getId(),
            order.getVersion().longValue(),
            new OrderApproved(
                order.getId(),
                order.getConsumerId(),
                order.getRestaurantId(),
                order.getOrderTotal(),
                order.getTicketId(),
                order.getAuthorizationId(),
                order.getPickupAddress(),
                toAddress(order.getDeliveryInfo().getDeliveryAddress()),
                order.getDeliveryInfo().getDeliveryTime()
            )
        );
        return true;
    }

    private Address toAddress(String deliveryAddress) {
        String[] streetCityStateZip = deliveryAddress.split(", ", 3);
        if (streetCityStateZip.length != 3) {
            return new Address(deliveryAddress, "Unknown", "NA", "00000");
        }
        String[] stateZip = streetCityStateZip[2].split(" ", 2);
        if (stateZip.length != 2) {
            return new Address(deliveryAddress, "Unknown", "NA", "00000");
        }
        return new Address(
            streetCityStateZip[0],
            streetCityStateZip[1],
            stateZip[0],
            stateZip[1]
        );
    }
}
