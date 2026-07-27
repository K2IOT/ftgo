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

    /**
     * Releases an unfinished claim after the participant handler throws so the
     * same Eventuate message ID can be retried. Implementations must only
     * remove claims that are still in the processing state.
     */
    default void abort(String consumerName, String commandId) {
    }

    Optional<ProcessedCommandResult> findCompleted(
        String consumerName,
        String commandId
    );
}