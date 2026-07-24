package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.ApproveTicketCommand;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import net.ftgo.common.orderflow.commands.CancelTicketCommand;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.common.orderflow.commands.ReleaseConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ReserveConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import net.ftgo.common.orderflow.menu.OrderMenuLineItem;
import net.ftgo.common.orderflow.replies.CardAuthorized;
import net.ftgo.common.orderflow.replies.ConsumerCreditReserved;
import net.ftgo.common.orderflow.replies.OrderMenuValidated;
import net.ftgo.common.orderflow.replies.TicketCreated;

import java.util.List;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/**
 * Prepares all remote resources required for an order and then waits for the
 * restaurant's asynchronous acceptance decision.
 *
 * <p>No step in this saga is a pivot. Every completed participant operation
 * has an idempotent compensation, so any failure before the final local step
 * converges to a rejected order with no held credit or authorization.</p>
 */
public class CreateOrderSaga implements SimpleSaga<CreateOrderSagaData> {

    private final CreateOrderSagaLocalSteps localSteps;
    private final SagaDefinition<CreateOrderSagaData> sagaDefinition;

    public CreateOrderSaga() {
        this(null);
    }

    public CreateOrderSaga(CreateOrderSagaLocalSteps localSteps) {
        this.localSteps = localSteps;
        this.sagaDefinition = step()
            .withCompensation(this::rejectOrder)
        .step()
            .invokeParticipant(this::validateMenu)
            .onReply(OrderMenuValidated.class, this::handleMenuValidated)
        .step()
            .invokeLocal(this::snapshotPickupAddress)
        .step()
            .invokeParticipant(this::reserveCredit)
            .onReply(ConsumerCreditReserved.class, this::handleCreditReserved)
            .withCompensation(this::releaseCredit)
        .step()
            .invokeParticipant(this::createTicket)
            .onReply(TicketCreated.class, this::handleTicketCreated)
            .withCompensation(this::cancelTicket)
        .step()
            .invokeParticipant(this::authorizeCard)
            .onReply(CardAuthorized.class, this::handleCardAuthorized)
            .withCompensation(this::voidAuthorization)
        .step()
            .invokeParticipant(this::approveTicket)
        .step()
            .invokeLocal(this::awaitRestaurantAcceptance)
        .build();
    }

    @Override
    public SagaDefinition<CreateOrderSagaData> getSagaDefinition() {
        return sagaDefinition;
    }

    private CommandWithDestination rejectOrder(CreateOrderSagaData data) {
        return send(new CreateOrderSagaLocalSteps.RejectOrderCommand(data.getOrderId()))
            .to(ChannelNames.CREATE_ORDER_SAGA_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination validateMenu(CreateOrderSagaData data) {
        return send(new ValidateOrderMenuCommand(
            data.getOrderId(),
            data.getRestaurantId(),
            data.getExpectedMenuVersion(),
            requestedMenuItems(data)
        ))
            .to(ChannelNames.RESTAURANT_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void handleMenuValidated(CreateOrderSagaData data, OrderMenuValidated reply) {
        data.setPickupAddress(reply.getPickupAddress());
        data.setAuthoritativeMenuItems(reply.getAuthoritativeLineItems());
        data.setAuthoritativeTotal(reply.getAuthoritativeTotal());
    }

    private void snapshotPickupAddress(CreateOrderSagaData data) {
        requireLocalSteps().snapshotPickupAddress(data);
    }

    private CommandWithDestination reserveCredit(CreateOrderSagaData data) {
        return send(new ReserveConsumerCreditCommand(
            data.getConsumerId(),
            data.getOrderId(),
            data.getOrderTotal()
        ))
            .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void handleCreditReserved(CreateOrderSagaData data, ConsumerCreditReserved reply) {
        data.setCreditReservationId(reply.getReservationId());
    }

    private CommandWithDestination releaseCredit(CreateOrderSagaData data) {
        return send(new ReleaseConsumerCreditCommand(
            data.getConsumerId(),
            data.getOrderId(),
            compensationReason(data)
        ))
            .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination createTicket(CreateOrderSagaData data) {
        List<OrderMenuLineItem> items = authoritativeMenuItems(data);
        return send(new CreateTicketCommand(
            data.getOrderId(),
            data.getRestaurantId(),
            items.stream()
                .map(item -> new CreateTicketCommand.TicketLineItemDTO(
                    item.getMenuItemId(),
                    item.getExpectedName(),
                    item.getQuantity()
                ))
                .toList()
        ))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void handleTicketCreated(CreateOrderSagaData data, TicketCreated reply) {
        data.setTicketId(reply.getTicketId());
    }

    private CommandWithDestination cancelTicket(CreateOrderSagaData data) {
        return send(new CancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination authorizeCard(CreateOrderSagaData data) {
        return send(new AuthorizeCardCommand(
            data.getConsumerId(),
            data.getOrderId(),
            data.getOrderTotal(),
            data.getPaymentToken(),
            requestId(data, "authorize")
        ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void handleCardAuthorized(CreateOrderSagaData data, CardAuthorized reply) {
        data.setAuthorizationId(reply.getAuthorizationId());
    }

    private CommandWithDestination voidAuthorization(CreateOrderSagaData data) {
        return send(new VoidAuthorizationCommand(
            data.getOrderId(),
            data.getAuthorizationId(),
            compensationReason(data),
            requestId(data, "void-create-compensation")
        ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination approveTicket(CreateOrderSagaData data) {
        return send(new ApproveTicketCommand(
            data.getTicketId(),
            data.getAcceptanceDeadline()
        ))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void awaitRestaurantAcceptance(CreateOrderSagaData data) {
        requireLocalSteps().awaitRestaurantAcceptance(data);
    }

    private List<OrderMenuLineItem> requestedMenuItems(CreateOrderSagaData data) {
        if (data.getRequestedMenuItems() != null && !data.getRequestedMenuItems().isEmpty()) {
            return data.getRequestedMenuItems();
        }
        return data.getLineItems().stream()
            .map(item -> new OrderMenuLineItem(
                item.getMenuItemId(),
                item.getName(),
                item.getPrice(),
                item.getQuantity()
            ))
            .toList();
    }

    private List<OrderMenuLineItem> authoritativeMenuItems(CreateOrderSagaData data) {
        if (data.getAuthoritativeMenuItems() != null && !data.getAuthoritativeMenuItems().isEmpty()) {
            return data.getAuthoritativeMenuItems();
        }
        return requestedMenuItems(data);
    }

    private String compensationReason(CreateOrderSagaData data) {
        return data.getFailureCode() == null
            ? "CREATE_ORDER_COMPENSATION"
            : data.getFailureCode();
    }

    private String requestId(CreateOrderSagaData data, String operation) {
        return "order-" + data.getOrderId() + "-" + operation;
    }

    private CreateOrderSagaLocalSteps requireLocalSteps() {
        if (localSteps == null) {
            throw new IllegalStateException(
                "CreateOrderSagaLocalSteps is required to execute CreateOrderSaga"
            );
        }
        return localSteps;
    }
}
