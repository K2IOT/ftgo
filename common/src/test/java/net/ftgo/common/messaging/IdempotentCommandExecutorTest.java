package net.ftgo.common.messaging;

import io.eventuate.tram.commands.common.ReplyMessageHeaders;
import io.eventuate.tram.messaging.common.Message;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotentCommandExecutorTest {

    @Test
    void duplicateCommandReplaysOriginalSuccessReplyWithoutCallingHandlerAgain() {
        InMemoryStore store = new InMemoryStore();
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(store);
        AtomicInteger calls = new AtomicInteger();

        Message first = executor.execute("kitchen-service", "command-123", () -> {
            calls.incrementAndGet();
            return withSuccess(new TestReply("ticket-901"));
        });
        Message duplicate = executor.execute("kitchen-service", "command-123", () -> {
            calls.incrementAndGet();
            return withSuccess(new TestReply("ticket-should-not-exist"));
        });

        assertEquals(1, calls.get());
        assertSameReply(first, duplicate);
    }

    @Test
    void duplicateCommandReplaysOriginalTypedFailure() {
        InMemoryStore store = new InMemoryStore();
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(store);
        AtomicInteger calls = new AtomicInteger();

        Message first = executor.execute("consumer-service", "command-456", () -> {
            calls.incrementAndGet();
            return withFailure(new TestReply("INSUFFICIENT_CREDIT"));
        });
        Message duplicate = executor.execute("consumer-service", "command-456", () -> {
            calls.incrementAndGet();
            return withSuccess(new TestReply("incorrect-success"));
        });

        assertEquals(1, calls.get());
        assertSameReply(first, duplicate);
    }

    private void assertSameReply(Message expected, Message actual) {
        assertEquals(expected.getPayload(), actual.getPayload());
        assertEquals(
            expected.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME),
            actual.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME)
        );
        assertEquals(
            expected.getRequiredHeader(ReplyMessageHeaders.REPLY_TYPE),
            actual.getRequiredHeader(ReplyMessageHeaders.REPLY_TYPE)
        );
    }

    private record TestReply(String id) {
    }

    private static final class InMemoryStore implements ProcessedCommandStore {
        private final Map<String, ProcessedCommandResult> completed = new HashMap<>();
        private final Map<String, Boolean> claimed = new HashMap<>();

        @Override
        public boolean tryStart(String consumerName, String commandId) {
            return claimed.putIfAbsent(key(consumerName, commandId), true) == null;
        }

        @Override
        public void complete(
            String consumerName,
            String commandId,
            ProcessedCommandResult result
        ) {
            completed.put(key(consumerName, commandId), result);
        }

        @Override
        public Optional<ProcessedCommandResult> findCompleted(
            String consumerName,
            String commandId
        ) {
            return Optional.ofNullable(completed.get(key(consumerName, commandId)));
        }

        private String key(String consumerName, String commandId) {
            return consumerName + ":" + commandId;
        }
    }
}
