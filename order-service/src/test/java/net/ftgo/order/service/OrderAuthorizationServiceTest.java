package net.ftgo.order.service;

import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAuthorizationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new OrderAuthorizationService(orderRepository);
    }

    @Test
    void permitsConsumerToAccessOwnOrder() {
        Order order = orderForConsumer(101L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order authorized = authorizationService.requireOwner(1L, consumer(101L));

        assertSame(order, authorized);
    }

    @Test
    void rejectsConsumerAccessToAnotherConsumersOrder() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(orderForConsumer(202L)));

        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireOwner(1L, consumer(101L))
        );
    }

    @Test
    void rejectsConsumerTokenWithoutConsumerIdentity() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(orderForConsumer(202L)));
        FtgoPrincipal principal = new FtgoPrincipal(
            "consumer-without-id",
            null,
            Set.of(),
            null,
            Set.of("CONSUMER"),
            Set.of("ftgo-api")
        );

        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireOwner(1L, principal)
        );
    }

    @Test
    void permitsExplicitAdminBypass() {
        Order order = orderForConsumer(202L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        FtgoPrincipal admin = new FtgoPrincipal(
            "admin-user",
            null,
            Set.of(),
            null,
            Set.of("ADMIN"),
            Set.of("ftgo-api")
        );

        Order authorized = authorizationService.requireOwner(1L, admin);

        assertSame(order, authorized);
    }

    @Test
    void preservesNotFoundSemanticsForUnknownOrder() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(
            OrderNotFoundException.class,
            () -> authorizationService.requireOwner(404L, consumer(101L))
        );
    }

    private FtgoPrincipal consumer(Long consumerId) {
        return new FtgoPrincipal(
            "consumer-" + consumerId,
            consumerId,
            Set.of(),
            null,
            Set.of("CONSUMER"),
            Set.of("ftgo-api")
        );
    }

    private Order orderForConsumer(Long consumerId) {
        return new Order(
            consumerId,
            501L,
            List.of(new OrderLineItem(10L, "Burger", new Money("12.99"), 1)),
            new DeliveryInfo("123 Main St", LocalDateTime.now().plusHours(1)),
            new PaymentInfo("tok_test")
        );
    }
}
