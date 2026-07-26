package net.ftgo.order.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRestaurantDecisionTest {

    @Test
    void createFlowEndsWaitingForRestaurantInsteadOfApproved() {
        Order order = newOrder();
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(5);

        order.awaitRestaurantAcceptance(401L, 501L, 601L, deadline);

        assertThat(order.getState()).isEqualTo(OrderState.AWAITING_RESTAURANT_ACCEPTANCE);
        assertThat(order.getTicketId()).isEqualTo(401L);
        assertThat(order.getAuthorizationId()).isEqualTo(501L);
        assertThat(order.getCreditReservationId()).isEqualTo(601L);
        assertThat(order.getAcceptanceDeadline()).isEqualTo(deadline);
        assertThat(order.isPending()).isTrue();
    }

    @Test
    void onlyOneRestaurantDecisionCanBeClaimed() {
        Order order = awaitingOrder();

        assertThat(order.claimRestaurantAcceptance()).isTrue();
        assertThat(order.claimRestaurantAcceptance()).isFalse();
        assertThat(order.claimRestaurantRejection("ACCEPTANCE_TIMEOUT", "Timed out")).isFalse();
        assertThat(order.getState()).isEqualTo(OrderState.CONFIRMATION_PENDING);
    }

    @Test
    void rejectionClaimStoresSafeReasonAndBlocksAcceptance() {
        Order order = awaitingOrder();

        assertThat(order.claimRestaurantRejection("RESTAURANT_REJECTED", "Restaurant declined")).isTrue();
        assertThat(order.claimRestaurantRejection("RESTAURANT_REJECTED", "Restaurant declined")).isFalse();
        assertThat(order.claimRestaurantAcceptance()).isFalse();
        assertThat(order.getState()).isEqualTo(OrderState.REJECTION_PENDING);
        assertThat(order.getRejectionCode()).isEqualTo("RESTAURANT_REJECTED");
        assertThat(order.getRejectionMessage()).isEqualTo("Restaurant declined");
    }

    @Test
    void confirmAndRejectCompletionAreIdempotent() {
        Order accepted = awaitingOrder();
        accepted.claimRestaurantAcceptance();
        assertThat(accepted.confirmRestaurantAcceptance()).isTrue();
        assertThat(accepted.confirmRestaurantAcceptance()).isFalse();
        assertThat(accepted.getState()).isEqualTo(OrderState.APPROVED);

        Order rejected = awaitingOrder();
        rejected.claimRestaurantRejection("ACCEPTANCE_TIMEOUT", "Timed out");
        assertThat(rejected.completeRestaurantRejection()).isTrue();
        assertThat(rejected.completeRestaurantRejection()).isFalse();
        assertThat(rejected.getState()).isEqualTo(OrderState.REJECTED);
    }

    @Test
    void waitingTransitionRequiresAllRemoteResourceIdentifiers() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.awaitRestaurantAcceptance(
            null,
            501L,
            601L,
            LocalDateTime.now().plusMinutes(5)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Order awaitingOrder() {
        Order order = newOrder();
        ReflectionTestUtils.setField(order, "id", 101L);
        order.awaitRestaurantAcceptance(
            401L,
            501L,
            601L,
            LocalDateTime.now().plusMinutes(5)
        );
        return order;
    }

    private Order newOrder() {
        return new Order(
            301L,
            202L,
            List.of(new OrderLineItem(11L, "Burger", new Money("25.00"), 1)),
            new DeliveryInfo("1 Main St, Hanoi, HN 10000", LocalDateTime.now().plusHours(1)),
            new PaymentInfo("tok_phase_02")
        );
    }
}
