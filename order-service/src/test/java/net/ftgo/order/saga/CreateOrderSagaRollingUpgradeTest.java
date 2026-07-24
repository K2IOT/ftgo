package net.ftgo.order.saga;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderSagaRollingUpgradeTest {

    @Test
    void phase02aPreservesThePhase02SagaStepTopology() throws Exception {
        Path source = findRepoRoot().resolve(
            "order-service/src/main/java/net/ftgo/order/saga/CreateOrderSaga.java"
        );
        String sagaSource = Files.readString(source);

        assertThat(countOccurrences(sagaSource, "step()"))
            .as("inserting steps shifts persisted Eventuate saga step indexes")
            .isEqualTo(7);
        assertThat(sagaSource)
            .doesNotContain("invokeLocal(this::snapshotPickupAddress)")
            .containsSubsequence(
                "withCompensation(this::rejectOrder)",
                "invokeParticipant(this::validateMenu)",
                "invokeParticipant(this::reserveCredit)",
                "invokeParticipant(this::createTicket)",
                "invokeParticipant(this::authorizeCard)",
                "invokeParticipant(this::approveTicket)",
                "invokeLocal(this::awaitRestaurantAcceptance)"
            );
    }

    private int countOccurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not find repository root");
        }
        return current;
    }
}
