package net.ftgo.common.messaging;

import java.util.Optional;

/** Storage boundary for claiming and replaying participant commands. */
public interface ProcessedCommandStore {

    boolean tryStart(String consumerName, String commandId);

    void complete(
        String consumerName,
        String commandId,
        ProcessedCommandResult result
    );

    Optional<ProcessedCommandResult> findCompleted(
        String consumerName,
        String commandId
    );
}
