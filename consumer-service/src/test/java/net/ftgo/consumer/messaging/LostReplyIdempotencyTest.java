package net.ftgo.consumer.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.orderflow.commands.ReserveConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.VerifyConsumerCommand;
import net.ftgo.consumer.domain.CreditReservation;
import net.ftgo.consumer.service.ConsumerService;
import net.ftgo.consumer.service.CreditReservationService;
import net.ftgo.testsupport.IdempotentCommandContract;
import net.ftgo.testsupport.InMemoryProcessedCommandStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LostReplyIdempotencyTest {

    @Mock
    private ConsumerService consumerService;

    @Mock
    private CreditReservationService creditReservationService;

    @Test
    void duplicateReserveCreditCommandReplaysEstablishedReservationReply() {
        ConsumerCommandHandlers handlers = new ConsumerCommandHandlers(
            consumerService,
            creditReservationService,
            new IdempotentCommandExecutor(new InMemoryProcessedCommandStore())
        );
        Money amount = new Money("42.50");
        ReserveConsumerCreditCommand command = new ReserveConsumerCreditCommand(
            202L,
            101L,
            amount
        );
        CommandMessage<ReserveConsumerCreditCommand> message = new CommandMessage<>(
            "eventuate-command-reservation-101",
            command,
            Map.of(),
            mock(Message.class)
        );
        CreditReservation reservation = new CreditReservation(202L, 101L, amount);
        ReflectionTestUtils.setField(reservation, "id", 601L);
        when(creditReservationService.reserve(202L, 101L, amount)).thenReturn(reservation);

        Message first = handlers.handleReserveCredit(message);
        Message replayed = handlers.handleReserveCredit(message);

        IdempotentCommandContract.assertByteEquivalentReply(first, replayed);
        verify(creditReservationService, times(1)).reserve(202L, 101L, amount);
    }

    @Test
    void unexpectedVerificationFailureIsNotCachedAsSagaReply() {
        InMemoryProcessedCommandStore store = new InMemoryProcessedCommandStore();
        ConsumerCommandHandlers handlers = new ConsumerCommandHandlers(
            consumerService,
            creditReservationService,
            new IdempotentCommandExecutor(store)
        );
        Money amount = new Money("42.50");
        VerifyConsumerCommand command = new VerifyConsumerCommand(202L, amount);
        CommandMessage<VerifyConsumerCommand> message = new CommandMessage<>(
            "eventuate-command-verify-202",
            command,
            Map.of(),
            mock(Message.class)
        );
        when(consumerService.verifyConsumerCredit(202L, amount))
            .thenThrow(new DataAccessResourceFailureException("consumer database unavailable"));

        assertThrows(
            DataAccessResourceFailureException.class,
            () -> handlers.handleVerifyConsumer(message)
        );
        assertTrue(store.findCompleted("consumer-service", "eventuate-command-verify-202").isEmpty());
    }
}
