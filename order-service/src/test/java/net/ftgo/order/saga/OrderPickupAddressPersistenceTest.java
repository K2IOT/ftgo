package net.ftgo.order.saga;

import jakarta.persistence.EntityManager;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPickupAddressPersistenceTest extends OrderServiceIntegrationTestBase {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CreateOrderSagaLocalSteps localSteps;

    @Test
    @Transactional
    void pickupAddressPersistsSeparatelyAndCannotBeReplaced() {
        Address pickup = new Address("10 Kitchen Road", "Bangkok", "Bangkok", "10110");
        Address delivery = new Address("99 Consumer Avenue", "Bangkok", "Bangkok", "10260");
        Order order = new Order(
            7L,
            42L,
            List.of(new OrderLineItem(11L, "Burger", new Money("12.50"), 1)),
            new DeliveryInfo(delivery, LocalDateTime.now().plusHours(1)),
            new PaymentInfo("tok-phase-02a")
        );

        order.snapshotPickupAddress(pickup);
        Order saved = orderRepository.saveAndFlush(order);
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPickupAddress()).isEqualTo(pickup);
        assertThat(reloaded.getDeliveryInfo().getDeliveryAddress()).isNotEqualTo(pickup.getFullAddress());
        assertThatThrownBy(() -> reloaded.snapshotPickupAddress(
            new Address("20 New Road", "Bangkok", "Bangkok", "10120")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("immutable");
    }

    @Test
    @Transactional
    void stableFinalCreateSagaStepPersistsSnapshotAndResourcesAtomically() {
        Address pickup = new Address("10 Kitchen Road", "Bangkok", "Bangkok", "10110");
        Address delivery = new Address("99 Consumer Avenue", "Bangkok", "Bangkok", "10260");
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(5).withNano(0);
        Order saved = orderRepository.saveAndFlush(new Order(
            7L,
            42L,
            List.of(new OrderLineItem(11L, "Burger", new Money("12.50"), 1)),
            new DeliveryInfo(delivery, LocalDateTime.now().plusHours(1)),
            new PaymentInfo("tok-phase-02a")
        ));

        CreateOrderSagaData data = new CreateOrderSagaData();
        data.setOrderId(saved.getId());
        data.setPickupAddress(pickup);
        data.setTicketId(301L);
        data.setAuthorizationId(401L);
        data.setCreditReservationId(501L);
        data.setAcceptanceDeadline(deadline);

        assertThat(localSteps.awaitRestaurantAcceptance(data)).isTrue();
        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPickupAddress()).isEqualTo(pickup);
        assertThat(reloaded.getState()).isEqualTo(OrderState.AWAITING_RESTAURANT_ACCEPTANCE);
        assertThat(reloaded.getTicketId()).isEqualTo(301L);
        assertThat(reloaded.getAuthorizationId()).isEqualTo(401L);
        assertThat(reloaded.getCreditReservationId()).isEqualTo(501L);
        assertThat(reloaded.getAcceptanceDeadline()).isEqualTo(deadline);
    }
}
