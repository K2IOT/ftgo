package net.ftgo.common.orderflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.CommitConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.RefundPaymentCommand;
import net.ftgo.common.orderflow.commands.ReleaseConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ReserveConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import net.ftgo.common.orderflow.events.TicketAcceptanceTimedOutEvent;
import net.ftgo.common.orderflow.events.TicketAcceptedEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.common.orderflow.menu.OrderMenuLineItem;
import net.ftgo.common.orderflow.replies.AuthorizationVoided;
import net.ftgo.common.orderflow.replies.ConsumerCreditCommitted;
import net.ftgo.common.orderflow.replies.ConsumerCreditReleased;
import net.ftgo.common.orderflow.replies.ConsumerCreditReservationRejected;
import net.ftgo.common.orderflow.replies.ConsumerCreditReserved;
import net.ftgo.common.orderflow.replies.OrderMenuValidated;
import net.ftgo.common.orderflow.replies.OrderMenuValidationRejected;
import net.ftgo.common.orderflow.replies.PaymentCaptured;
import net.ftgo.common.orderflow.replies.PaymentRefunded;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class Phase02OrderFlowContractsSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void menuValidationContractsRoundTrip() throws Exception {
        OrderMenuLineItem burger = new OrderMenuLineItem(11L, "Burger", new Money("12.50"), 2);
        ValidateOrderMenuCommand command = new ValidateOrderMenuCommand(
            101L,
            202L,
            7L,
            List.of(burger)
        );

        ValidateOrderMenuCommand parsedCommand = roundTrip(command, ValidateOrderMenuCommand.class);
        assertInstanceOf(Command.class, parsedCommand);
        assertEquals(101L, parsedCommand.getOrderId());
        assertEquals(202L, parsedCommand.getRestaurantId());
        assertEquals(7L, parsedCommand.getExpectedMenuVersion());
        assertEquals("Burger", parsedCommand.getLineItems().getFirst().getExpectedName());
        assertEquals(new Money("12.50"), parsedCommand.getLineItems().getFirst().getExpectedUnitPrice());
        assertEquals(2, parsedCommand.getLineItems().getFirst().getQuantity());

        OrderMenuValidated validated = new OrderMenuValidated(
            101L,
            202L,
            7L,
            List.of(burger),
            new Money("25.00")
        );
        OrderMenuValidated parsedValidated = roundTrip(validated, OrderMenuValidated.class);
        assertEquals(101L, parsedValidated.getOrderId());
        assertEquals(202L, parsedValidated.getRestaurantId());
        assertEquals(7L, parsedValidated.getCurrentMenuVersion());
        assertEquals(new Money("25.00"), parsedValidated.getAuthoritativeTotal());

        OrderMenuValidationRejected rejected = new OrderMenuValidationRejected(
            101L,
            OrderMenuValidationRejected.MENU_PRICE_CHANGED,
            "Menu price changed"
        );
        OrderMenuValidationRejected parsedRejected = roundTrip(rejected, OrderMenuValidationRejected.class);
        assertEquals(101L, parsedRejected.getOrderId());
        assertEquals(OrderMenuValidationRejected.MENU_PRICE_CHANGED, parsedRejected.getReasonCode());
        assertEquals("Menu price changed", parsedRejected.getMessage());
    }

    @Test
    void creditReservationContractsRoundTrip() throws Exception {
        ReserveConsumerCreditCommand reserve = new ReserveConsumerCreditCommand(
            301L,
            101L,
            new Money("25.00")
        );
        ReserveConsumerCreditCommand parsedReserve = roundTrip(reserve, ReserveConsumerCreditCommand.class);
        assertInstanceOf(Command.class, parsedReserve);
        assertEquals(301L, parsedReserve.getConsumerId());
        assertEquals(101L, parsedReserve.getOrderId());
        assertEquals(new Money("25.00"), parsedReserve.getAmount());

        CommitConsumerCreditCommand commit = new CommitConsumerCreditCommand(301L, 101L);
        CommitConsumerCreditCommand parsedCommit = roundTrip(commit, CommitConsumerCreditCommand.class);
        assertInstanceOf(Command.class, parsedCommit);
        assertEquals(301L, parsedCommit.getConsumerId());
        assertEquals(101L, parsedCommit.getOrderId());

        ReleaseConsumerCreditCommand release = new ReleaseConsumerCreditCommand(
            301L,
            101L,
            "RESTAURANT_REJECTED"
        );
        ReleaseConsumerCreditCommand parsedRelease = roundTrip(release, ReleaseConsumerCreditCommand.class);
        assertInstanceOf(Command.class, parsedRelease);
        assertEquals(301L, parsedRelease.getConsumerId());
        assertEquals(101L, parsedRelease.getOrderId());
        assertEquals("RESTAURANT_REJECTED", parsedRelease.getReason());

        ConsumerCreditReserved reserved = new ConsumerCreditReserved(401L, 101L, new Money("25.00"));
        ConsumerCreditReserved parsedReserved = roundTrip(reserved, ConsumerCreditReserved.class);
        assertEquals(401L, parsedReserved.getReservationId());
        assertEquals(101L, parsedReserved.getOrderId());
        assertEquals(new Money("25.00"), parsedReserved.getAmount());

        ConsumerCreditCommitted committed = new ConsumerCreditCommitted(401L, 101L);
        ConsumerCreditCommitted parsedCommitted = roundTrip(committed, ConsumerCreditCommitted.class);
        assertEquals(401L, parsedCommitted.getReservationId());
        assertEquals(101L, parsedCommitted.getOrderId());

        ConsumerCreditReleased released = new ConsumerCreditReleased(401L, 101L);
        ConsumerCreditReleased parsedReleased = roundTrip(released, ConsumerCreditReleased.class);
        assertEquals(401L, parsedReleased.getReservationId());
        assertEquals(101L, parsedReleased.getOrderId());

        ConsumerCreditReservationRejected rejected = new ConsumerCreditReservationRejected(
            101L,
            ConsumerCreditReservationRejected.INSUFFICIENT_CREDIT,
            "Insufficient credit"
        );
        ConsumerCreditReservationRejected parsedRejected = roundTrip(
            rejected,
            ConsumerCreditReservationRejected.class
        );
        assertEquals(101L, parsedRejected.getOrderId());
        assertEquals(ConsumerCreditReservationRejected.INSUFFICIENT_CREDIT, parsedRejected.getReasonCode());
        assertEquals("Insufficient credit", parsedRejected.getMessage());
    }

    @Test
    void paymentLifecycleContractsRoundTrip() throws Exception {
        AuthorizeCardCommand authorize = new AuthorizeCardCommand(
            301L,
            101L,
            new Money("25.00"),
            "order-101-authorize"
        );
        AuthorizeCardCommand parsedAuthorize = roundTrip(authorize, AuthorizeCardCommand.class);
        assertInstanceOf(Command.class, parsedAuthorize);
        assertEquals(301L, parsedAuthorize.getConsumerId());
        assertEquals(101L, parsedAuthorize.getOrderId());
        assertEquals(new Money("25.00"), parsedAuthorize.getAmount());
        assertEquals("order-101-authorize", parsedAuthorize.getRequestId());

        CaptureAuthorizationCommand capture = new CaptureAuthorizationCommand(
            101L,
            501L,
            "order-101-capture"
        );
        CaptureAuthorizationCommand parsedCapture = roundTrip(capture, CaptureAuthorizationCommand.class);
        assertInstanceOf(Command.class, parsedCapture);
        assertEquals(101L, parsedCapture.getOrderId());
        assertEquals(501L, parsedCapture.getAuthorizationId());
        assertEquals("order-101-capture", parsedCapture.getRequestId());

        VoidAuthorizationCommand voidCommand = new VoidAuthorizationCommand(
            101L,
            501L,
            "RESTAURANT_TIMEOUT",
            "order-101-void"
        );
        VoidAuthorizationCommand parsedVoid = roundTrip(voidCommand, VoidAuthorizationCommand.class);
        assertInstanceOf(Command.class, parsedVoid);
        assertEquals(101L, parsedVoid.getOrderId());
        assertEquals(501L, parsedVoid.getAuthorizationId());
        assertEquals("RESTAURANT_TIMEOUT", parsedVoid.getReason());
        assertEquals("order-101-void", parsedVoid.getRequestId());

        RefundPaymentCommand refund = new RefundPaymentCommand(
            101L,
            601L,
            new Money("25.00"),
            "ORDER_CANCELLED",
            "order-101-refund-1"
        );
        RefundPaymentCommand parsedRefund = roundTrip(refund, RefundPaymentCommand.class);
        assertInstanceOf(Command.class, parsedRefund);
        assertEquals(101L, parsedRefund.getOrderId());
        assertEquals(601L, parsedRefund.getCaptureId());
        assertEquals(new Money("25.00"), parsedRefund.getAmount());
        assertEquals("ORDER_CANCELLED", parsedRefund.getReason());
        assertEquals("order-101-refund-1", parsedRefund.getRequestId());

        PaymentCaptured captured = roundTrip(new PaymentCaptured(601L, 501L, 101L), PaymentCaptured.class);
        assertEquals(601L, captured.getCaptureId());
        assertEquals(501L, captured.getAuthorizationId());
        assertEquals(101L, captured.getOrderId());

        AuthorizationVoided voided = roundTrip(new AuthorizationVoided(501L, 101L), AuthorizationVoided.class);
        assertEquals(501L, voided.getAuthorizationId());
        assertEquals(101L, voided.getOrderId());

        PaymentRefunded refunded = roundTrip(new PaymentRefunded(701L, 601L, 101L), PaymentRefunded.class);
        assertEquals(701L, refunded.getRefundId());
        assertEquals(601L, refunded.getCaptureId());
        assertEquals(101L, refunded.getOrderId());
    }

    @Test
    void ticketDecisionEventsRoundTrip() throws Exception {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 23, 10, 15, 30);
        LocalDateTime deadline = LocalDateTime.of(2026, 7, 23, 10, 10, 0);

        TicketAcceptedEvent accepted = roundTrip(
            new TicketAcceptedEvent("accept-901", 901L, 101L, occurredAt),
            TicketAcceptedEvent.class
        );
        assertEquals("accept-901", accepted.getEventId());
        assertEquals(901L, accepted.getTicketId());
        assertEquals(101L, accepted.getOrderId());
        assertEquals(occurredAt, accepted.getOccurredAt());

        TicketRejectedEvent rejected = roundTrip(
            new TicketRejectedEvent("reject-901", 901L, 101L, "RESTAURANT_REJECTED", occurredAt),
            TicketRejectedEvent.class
        );
        assertEquals("reject-901", rejected.getEventId());
        assertEquals(901L, rejected.getTicketId());
        assertEquals(101L, rejected.getOrderId());
        assertEquals("RESTAURANT_REJECTED", rejected.getReason());
        assertEquals(occurredAt, rejected.getOccurredAt());

        TicketAcceptanceTimedOutEvent timedOut = roundTrip(
            new TicketAcceptanceTimedOutEvent("timeout-901", 901L, 101L, deadline, occurredAt),
            TicketAcceptanceTimedOutEvent.class
        );
        assertEquals("timeout-901", timedOut.getEventId());
        assertEquals(901L, timedOut.getTicketId());
        assertEquals(101L, timedOut.getOrderId());
        assertEquals(deadline, timedOut.getAcceptanceDeadline());
        assertEquals(occurredAt, timedOut.getOccurredAt());
    }

    private <T> T roundTrip(T value, Class<T> type) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsString(value), type);
    }
}
