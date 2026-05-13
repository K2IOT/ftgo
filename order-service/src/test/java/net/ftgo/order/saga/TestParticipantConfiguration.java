package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.ApproveTicketCommand;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import net.ftgo.common.orderflow.commands.CancelTicketCommand;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.common.orderflow.commands.VerifyConsumerCommand;
import net.ftgo.common.orderflow.replies.CardAuthorized;
import net.ftgo.common.orderflow.replies.TicketCreated;
import net.ftgo.order.saga.commands.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

@Configuration
public class TestParticipantConfiguration {

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
                .onMessage(ReverseAuthorizationCommand.class, this::handleReverseAuthorization)
                .onMessage(ReviseAuthorizationCommand.class, this::handleReviseAuthorization)
                .build();
    }

    @Bean
    public CommandHandlers consumerCommandHandlers() {
        return SagaCommandHandlersBuilder
                .fromChannel(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
                .onMessage(VerifyConsumerCommand.class, this::handleVerifyConsumer)
                .build();
    }

    @Bean
    public CommandDispatcher kitchenTestCommandDispatcher(SagaCommandDispatcherFactory sagaCommandDispatcherFactory, CommandHandlers kitchenCommandHandlers) {
        return sagaCommandDispatcherFactory.make("kitchenTestCommandDispatcher", kitchenCommandHandlers);
    }

    @Bean
    public CommandDispatcher accountingTestCommandDispatcher(SagaCommandDispatcherFactory sagaCommandDispatcherFactory, CommandHandlers accountingCommandHandlers) {
        return sagaCommandDispatcherFactory.make("accountingTestCommandDispatcher", accountingCommandHandlers);
    }

    @Bean
    public CommandDispatcher consumerTestCommandDispatcher(SagaCommandDispatcherFactory sagaCommandDispatcherFactory, CommandHandlers consumerCommandHandlers) {
        return sagaCommandDispatcherFactory.make("consumerTestCommandDispatcher", consumerCommandHandlers);
    }

    private Message handleCreateTicket(CommandMessage<CreateTicketCommand> cm) {
        if (cm.getCommand().getRestaurantId() == 999L) {
            return withFailure();
        }
        return withSuccess(new TicketCreated(999L));
    }

    private Message handleApproveTicket(CommandMessage<ApproveTicketCommand> cm) {
        if (cm.getCommand().getTicketId() == 999L) {
            // Failure after pivot (simulate some specific failure logic if needed, but approveTicket should eventually succeed if retried. For testing failure after pivot, we might simulate temporary failure).
            // Actually let's just let it succeed for simplicity unless we specifically want a permanent failure which wouldn't make sense after pivot.
        }
        return withSuccess();
    }

    private Message handleCancelTicket(CommandMessage<CancelTicketCommand> cm) {
        return withSuccess();
    }

    private Message handleBeginCancelTicket(CommandMessage<BeginCancelTicketCommand> cm) {
        return withSuccess();
    }

    private Message handleConfirmCancelTicket(CommandMessage<ConfirmCancelTicketCommand> cm) {
        return withSuccess();
    }

    private Message handleUndoCancelTicket(CommandMessage<UndoCancelTicketCommand> cm) {
        return withSuccess();
    }

    private Message handleBeginReviseTicket(CommandMessage<BeginReviseTicketCommand> cm) {
        return withSuccess();
    }

    private Message handleConfirmReviseTicket(CommandMessage<ConfirmReviseTicketCommand> cm) {
        return withSuccess();
    }

    private Message handleUndoReviseTicket(CommandMessage<UndoReviseTicketCommand> cm) {
        return withSuccess();
    }

    private Message handleAuthorizeCard(CommandMessage<AuthorizeCardCommand> cm) {
        return withSuccess(new CardAuthorized(888L));
    }

    private Message handleReverseAuthorization(CommandMessage<ReverseAuthorizationCommand> cm) {
        return withSuccess();
    }

    private Message handleReviseAuthorization(CommandMessage<ReviseAuthorizationCommand> cm) {
        return withSuccess();
    }

    private Message handleVerifyConsumer(CommandMessage<VerifyConsumerCommand> cm) {
        return withSuccess();
    }
}
