package net.ftgo.common.messaging;

import io.eventuate.tram.commands.common.ReplyMessageHeaders;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.messaging.producer.MessageBuilder;

import java.util.Objects;

/** Persisted command reply that can be reconstructed after a lost saga reply. */
public record ProcessedCommandResult(
    String outcome,
    String replyType,
    String replyPayload
) {
    private static final String PROCESSING = "PROCESSING";

    public ProcessedCommandResult {
        requireText(outcome, "outcome");
        if (PROCESSING.equals(outcome)) {
            throw new IllegalArgumentException("PROCESSING is not a completed command outcome");
        }
        Objects.requireNonNull(replyPayload, "replyPayload");
    }

    public static ProcessedCommandResult from(Message reply) {
        Objects.requireNonNull(reply, "reply");
        return new ProcessedCommandResult(
            reply.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME),
            reply.getHeader(ReplyMessageHeaders.REPLY_TYPE).orElse(null),
            reply.getPayload()
        );
    }

    public Message toMessage() {
        MessageBuilder builder = MessageBuilder
            .withPayload(replyPayload)
            .withHeader(ReplyMessageHeaders.REPLY_OUTCOME, outcome);
        if (replyType != null && !replyType.isBlank()) {
            builder.withHeader(ReplyMessageHeaders.REPLY_TYPE, replyType);
        }
        return builder.build();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}
