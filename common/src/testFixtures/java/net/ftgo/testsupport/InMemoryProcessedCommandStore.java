package net.ftgo.testsupport;

import net.ftgo.common.messaging.ProcessedCommandResult;
import net.ftgo.common.messaging.ProcessedCommandStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Test-only deterministic store for idempotent command contracts. */
public final class InMemoryProcessedCommandStore implements ProcessedCommandStore {

    private final Map<String, ProcessedCommandResult> completed = new HashMap<>();
    private final Map<String, Boolean> claimed = new HashMap<>();

    @Override
    public synchronized boolean tryStart(String consumerName, String commandId) {
        return claimed.putIfAbsent(key(consumerName, commandId), true) == null;
    }

    @Override
    public synchronized void complete(
        String consumerName,
        String commandId,
        ProcessedCommandResult result
    ) {
        completed.put(key(consumerName, commandId), result);
    }

    @Override
    public synchronized void abort(String consumerName, String commandId) {
        String key = key(consumerName, commandId);
        if (!completed.containsKey(key)) {
            claimed.remove(key);
        }
    }

    @Override
    public synchronized Optional<ProcessedCommandResult> findCompleted(
        String consumerName,
        String commandId
    ) {
        return Optional.ofNullable(completed.get(key(consumerName, commandId)));
    }

    private String key(String consumerName, String commandId) {
        return consumerName + ":" + commandId;
    }
}