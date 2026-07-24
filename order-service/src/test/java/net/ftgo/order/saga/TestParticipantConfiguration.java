package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.ApproveTicketCommand;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import net.ftgo.common.orderflow.commands.BeginCancelTicketCommand;
import net.ftgo.common.orderflow.commands.BeginReviseTicketCommand;
import net.ftgo.common.orderflow.commands.CancelTicketCommand;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.CommitConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ConfirmCancelTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmReviseTicketCommand;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.common.orderflow.commands.ReleaseConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ReserveConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ReverseAuthorizationCommand;
import net.ftgo.common.orderflow.commands.ReviseAuthorizationCommand;
import net.ftgo.common.orderflow.commands.UndoCancelTicketCommand;
import net.ftgo.common.orderflow.commands.UndoReviseTicketCommand;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.common.orderflow.commands.VerifyConsumerCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import net.ftgo.common.orderflow.replies.AuthorizationRevised;
import net.ftgo.common.orderflow.replies.AuthorizationVoided;
import net.ftgo.common.orderflow.replies.CardAuthorized;
import net.ftgo.common.orderflow.replies.ConsumerCreditCommitted;
import net.ftgo.common.orderflow.replies.ConsumerCreditReleased;
import net.ftgo.common.orderflow.replies.ConsumerCreditReserved;
import net.ftgo.common.orderflow.replies.OrderMenuValidated;
import net.ftgo.common.orderflow.replies.PaymentCaptured;
import net.ftgo.common.orderflow.replies.TicketCreated;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

@Configuration
public class TestParticipantConfiguration {

    @Bean
    public CommandHandlers restaurantCommandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.RESTAURANT_SERVICE_COMMAND_CHANNEL)
            .onMessage(ValidateOrderMenuCommand.class, this::handleValidateMenu)
            .build();
    }

    @Bean
    public CommandHandlers kitchenCommandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .onMessage(CreateTicketCommand.class, this::handleCreateTicket)
            .onMessage(ApproveTicketCommand.class, this::handleApproveTicket)
            .onMessage(CancelTicketCommand.class, this::handleCancelTicket)
            .onMessage(BeginCancelTicketCommand.class, this::handleBeginCancelTicket)
            .onMessage(ConfirmCancelTicketCommand.class, this::handleConfirmCancelTicket)
            .onMessage(UndoCancelTicketCommand.class, this::handleUndoCancelTicket)
            .onMessage(BeginReviseTicketCommand.class, this::handleBeginReviseTicket)
            .onMessage(ConfirmReviseTicketCommand.class, this::handleConfirmReviseTicket)
            .onMessage(UndoReviseTicketCommand.class, this::handleUndoReviseTicket)
            .build();
    }

    @Bean
    public CommandHandlers accountingCommandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .onMessage(AuthorizeCardCommand.class, this::handleAuthorizeCard)
            .onMessage(CaptureAuthorizationCommand.class, this::handleCaptureAuthorization)
            .onMessage(VoidAuthorizationCommand.class, this::handleVoidAuthorization)
            .onMessage(ReverseAuthorizationCommand.class, this::handleReverseAuthorization)
            .onMessage(ReviseAuthorizationCommand.class, this::handleReviseAuthorization)
            .build();
    }

    @Bean
    public CommandHandlers consumerCommandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .onMessage(VerifyConsumerCommand.class, this::handleVerifyConsumer)
            .onMessage(ReserveConsumerCreditCommand.class, this::handleReserveCredit)
            .onMessage(CommitConsumerCreditCommand.class, this::handleCommitCredit)
            .onMessage(ReleaseConsumerCreditCommand.class, this::handleReleaseCredit)
            .build();
    }

    @Bean
    public CommandDispatcher restaurantTestCommandDispatcher(
        SagaCommandDispatcherFactory factory,
        CommandHandlers restaurantCommandHandlers
    ) {
        return factory.make("restaurantTestCommandDispatcher", restaurantCommandHandlers);
    }

    @Bean
    public CommandDispatcher kitchenTestCommandDispatcher(
        SagaCommandDispatcherFactory factory,
        CommandHandlers kitchenCommandHandlers
    ) {
        return factory.make("kitchenTestCommandDispatcher", kitchenCommandHandlers);
    }

    @Bean
    public CommandDispatcher accountingTestCommandDispatcher(
        SagaCommandDispatcherFactory factory,
        CommandHandlers accountingCommandHandlers
    ) {
        return factory.make("accountingTestCommandDispatcher", accountingCommandHandlers);
    }

    @Bean
    public CommandDispatcher consumerTestCommandDispatcher(
        SagaCommandDispatcherFactory factory,
        CommandHandlers consumerCommandHandlers
    ) {
        return factory.make("consumerTestCommandDispatcher", consumerCommandHandlers);
    }

    private Message handleValidateMenu(CommandMessage<ValidateOrderMenuCommand> message) {
        ValidateOrderMenuCommand command = message.getCommand();
        Money total = command.getLineItems().stream()
            .map(item -> item.getExpectedUnitPrice().multiply(item.getQuantity()))
            .reduce(Money.ZERO, Money::add);
        return withSuccess(new OrderMenuValidated(
            command.getOrderId(),
            command.getRestaurantId(),
            command.getExpectedMenuVersion(),
            command.getLineItems(),
            total
        ));
    }

    private Message handleCreateTicket(CommandMessage<CreateTicketCommand> message) {
        if (message.getCommand().getRestaurantId() == 999L) {
            return withFailure();
        }
        return withSuccess(new TicketCreated(999L));
    }

    private Message handleApproveTicket(CommandMessage<ApproveTicketCommand> message) {
        return withSuccess();
    }

    private Message handleCancelTicket(CommandMessage<CancelTicketCommand> message) {
        return withSuccess();
    }

    private Message handleBeginCancelTicket(CommandMessage<BeginCancelTicketCommand> message) {
        return withSuccess();
    }

    private Message handleConfirmCancelTicket(CommandMessage<ConfirmCancelTicketCommand> message) {
        return withSuccess();
    }

    private Message handleUndoCancelTicket(CommandMessage<UndoCancelTicketCommand> message) {
        return withSuccess();
    }

    private Message handleBeginReviseTicket(CommandMessage<BeginReviseTicketCommand> message) {
        return withSuccess();
    }

    private Message handleConfirmReviseTicket(CommandMessage<ConfirmReviseTicketCommand> message) {
        return withSuccess();
    }

    private Message handleUndoReviseTicket(CommandMessage<UndoReviseTicketCommand> message) {
        return withSuccess();
    }

    private Message handleAuthorizeCard(CommandMessage<AuthorizeCardCommand> message) {
        return withSuccess(new CardAuthorized(888L, message.getCommand().getOrderId()));
    }

    private Message handleCaptureAuthorization(CommandMessage<CaptureAuthorizationCommand> message) {
        CaptureAuthorizationCommand command = message.getCommand();
        return withSuccess(new PaymentCaptured(
            command.getAuthorizationId(),
            command.getAuthorizationId(),
            command.getOrderId()
        ));
    }

    private Message handleVoidAuthorization(CommandMessage<VoidAuthorizationCommand> message) {
        VoidAuthorizationCommand command = message.getCommand();
        return withSuccess(new AuthorizationVoided(
            command.getAuthorizationId(),
            command.getOrderId()
        ));
    }

    private Message handleReverseAuthorization(CommandMessage<ReverseAuthorizationCommand> message) {
        return withSuccess();
    }

    private Message handleReviseAuthorization(CommandMessage<ReviseAuthorizationCommand> message) {
        return withSuccess(new AuthorizationRevised(777L));
    }

    private Message handleVerifyConsumer(CommandMessage<VerifyConsumerCommand> message) {
        return withSuccess();
    }

    private Message handleReserveCredit(CommandMessage<ReserveConsumerCreditCommand> message) {
        ReserveConsumerCreditCommand command = message.getCommand();
        return withSuccess(new ConsumerCreditReserved(777L, command.getOrderId(), command.getAmount()));
    }

    private Message handleCommitCredit(CommandMessage<CommitConsumerCreditCommand> message) {
        return withSuccess(new ConsumerCreditCommitted(777L, message.getCommand().getOrderId()));
    }

    private Message handleReleaseCredit(CommandMessage<ReleaseConsumerCreditCommand> message) {
        return withSuccess(new ConsumerCreditReleased(777L, message.getCommand().getOrderId()));
    }
}
