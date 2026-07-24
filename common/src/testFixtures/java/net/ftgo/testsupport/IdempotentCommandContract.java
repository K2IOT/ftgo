package net.ftgo.testsupport;

import io.eventuate.tram.commands.common.ReplyMessageHeaders;
import io.eventuate.tram.messaging.common.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shared assertions for lost-reply command replay tests. */
public final class IdempotentCommandContract {

    private IdempotentCommandContract() {
    }

    public static void assertByteEquivalentReply(Message expected, Message replayed) {
        assertEquals(expected.getPayload(), replayed.getPayload());
        assertEquals(
            expected.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME),
            replayed.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME)
        );
        assertEquals(
            expected.getRequiredHeader(ReplyMessageHeaders.REPLY_TYPE),
            replayed.getRequiredHeader(ReplyMessageHeaders.REPLY_TYPE)
        );
    }
}
