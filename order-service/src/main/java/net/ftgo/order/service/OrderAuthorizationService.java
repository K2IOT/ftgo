package net.ftgo.order.service;

import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.order.domain.Order;
import net.ftgo.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class OrderAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(OrderAuthorizationService.class);

    private final OrderRepository orderRepository;

    public OrderAuthorizationService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order requireOwner(Long orderId, FtgoPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (principal.roles().contains("ADMIN")) {
            log.info(
                "Admin order access granted actorSubject={} orderId={} ownerConsumerId={}",
                principal.subject(),
                orderId,
                order.getConsumerId()
            );
            return order;
        }

        Long authenticatedConsumerId = principal.consumerId();
        if (authenticatedConsumerId == null
            || !authenticatedConsumerId.equals(order.getConsumerId())) {
            log.warn(
                "Order ownership check denied actorSubject={} actorConsumerId={} orderId={}",
                principal.subject(),
                authenticatedConsumerId,
                orderId
            );
            throw new AccessDeniedException("Order access denied");
        }

        return order;
    }
}
