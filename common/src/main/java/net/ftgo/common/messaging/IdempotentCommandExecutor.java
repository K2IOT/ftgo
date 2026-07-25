package net.ftgo.common.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Executes a saga participant command once and replays the exact stored reply
 * when Eventuate redelivers the same command message ID.
 */
public class IdempotentCommandExecutor {

    private final ProcessedCommandStore store;

    public IdempotentCommandExecutor(ProcessedCommandStore store) {
        this.store = store;
    }

    @Transactional
    public Message execute(
        String consumerName,
        CommandMessage<?> commandMessage,
        Supplier<Message> handler
    ) {
        Objects.requireNonNull(commandMessage, "commandMessage");
        return execute(consumerName, commandMessage.getMessageId(), handler);
    }

    @Transactional
    public Message execute(
        String consumerName,
        String commandId,
        Supplier<Message> handler
    ) {
        requireText(consumerName, "consumerName");
        requireText(commandId, "commandId");
        Objects.requireNonNull(handler, "handler");

        if (store.tryStart(consumerName, commandId)) {
            Message reply = Objects.requireNonNull(
                handler.get(),
                "Participant command handler returned no reply"
            );
            ProcessedCommandResult result = ProcessedCommandResult.from(reply);
            store.complete(consumerName, commandId, result);
            return reply;
        }

        return store.findCompleted(consumerName, commandId)
            .map(ProcessedCommandResult::toMessage)
            .orElseThrow(() -> new IllegalStateException(
                "Duplicate command has no completed result: "
                    + consumerName + "/" + commandId
            ));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}
