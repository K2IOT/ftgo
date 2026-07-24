package net.ftgo.common.messaging;

import java.time.Instant;
import java.util.Objects;

/** Completed participant command and its replayable result. */
public record ProcessedCommand(
    String consumerName,
    String commandId,
    ProcessedCommandResult result,
    Instant processedAt
) {
    public ProcessedCommand {
        requireText(consumerName, "consumerName");
        requireText(commandId, "commandId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(processedAt, "processedAt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}
