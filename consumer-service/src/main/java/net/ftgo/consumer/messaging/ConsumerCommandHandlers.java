package net.ftgo.consumer.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.CommitConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ReleaseConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ReserveConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.VerifyConsumerCommand;
import net.ftgo.common.orderflow.replies.ConsumerCreditCommitted;
import net.ftgo.common.orderflow.replies.ConsumerCreditReleased;
import net.ftgo.common.orderflow.replies.ConsumerCreditReservationRejected;
import net.ftgo.common.orderflow.replies.ConsumerCreditReserved;
import net.ftgo.common.orderflow.replies.ConsumerVerified;
import net.ftgo.consumer.domain.CreditReservation;
import net.ftgo.consumer.service.ConsumerService;
import net.ftgo.consumer.service.CreditReservationException;
import net.ftgo.consumer.service.CreditReservationService;
import org.springframework.stereotype.Component;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

@Component
public class ConsumerCommandHandlers {

    private final ConsumerService consumerService;
    private final CreditReservationService creditReservationService;

    public ConsumerCommandHandlers(ConsumerService consumerService,
                                   CreditReservationService creditReservationService) {
        this.consumerService = consumerService;
        this.creditReservationService = creditReservationService;
    }

    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .onMessage(VerifyConsumerCommand.class, this::handleVerifyConsumer)
            .onMessage(ReserveConsumerCreditCommand.class, this::handleReserveCredit)
            .onMessage(CommitConsumerCreditCommand.class, this::handleCommitCredit)
            .onMessage(ReleaseConsumerCreditCommand.class, this::handleReleaseCredit)
            .build();
    }

    private Message handleVerifyConsumer(CommandMessage<VerifyConsumerCommand> message) {
        VerifyConsumerCommand command = message.getCommand();
        try {
            if (consumerService.verifyConsumerCredit(command.getConsumerId(), command.getOrderTotal())) {
                return withSuccess(new ConsumerVerified(command.getConsumerId()));
            }
            return withFailure("Insufficient credit limit");
        } catch (Exception e) {
            return withFailure(e.getMessage());
        }
    }

    private Message handleReserveCredit(CommandMessage<ReserveConsumerCreditCommand> message) {
        ReserveConsumerCreditCommand command = message.getCommand();
        try {
            CreditReservation reservation = creditReservationService.reserve(
                command.getConsumerId(), command.getOrderId(), command.getAmount());
            return withSuccess(new ConsumerCreditReserved(
                reservation.getId(), reservation.getOrderId(), reservation.getAmount()));
        } catch (CreditReservationException e) {
            return rejected(command.getOrderId(), e);
        }
    }

    private Message handleCommitCredit(CommandMessage<CommitConsumerCreditCommand> message) {
        CommitConsumerCreditCommand command = message.getCommand();
        try {
            CreditReservation reservation = creditReservationService.commit(
                command.getConsumerId(), command.getOrderId());
            return withSuccess(new ConsumerCreditCommitted(
                reservation.getId(), reservation.getOrderId()));
        } catch (CreditReservationException e) {
            return rejected(command.getOrderId(), e);
        }
    }

    private Message handleReleaseCredit(CommandMessage<ReleaseConsumerCreditCommand> message) {
        ReleaseConsumerCreditCommand command = message.getCommand();
        try {
            CreditReservation reservation = creditReservationService.release(
                command.getConsumerId(), command.getOrderId(), command.getReason());
            return withSuccess(new ConsumerCreditReleased(
                reservation.getId(), reservation.getOrderId()));
        } catch (CreditReservationException e) {
            return rejected(command.getOrderId(), e);
        }
    }

    private Message rejected(Long orderId, CreditReservationException e) {
        return withSuccess(new ConsumerCreditReservationRejected(
            orderId, e.getReasonCode(), e.getMessage()));
    }
}
