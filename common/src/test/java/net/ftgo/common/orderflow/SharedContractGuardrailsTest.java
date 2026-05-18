package net.ftgo.common.orderflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedContractGuardrailsTest {

    @Test
    void eventuateContractCodeDoesNotReferenceLegacyPackageLocalOrderContracts() throws Exception {
        Path repoRoot = findRepoRoot();

        List<Path> filesToCheck = List.of(
            repoRoot.resolve("order-service/src/main/java/net/ftgo/order/saga/CreateOrderSaga.java"),
            repoRoot.resolve("order-service/src/main/java/net/ftgo/order/saga/CancelOrderSaga.java"),
            repoRoot.resolve("order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSaga.java"),
            repoRoot.resolve("order-history-service/src/main/java/net/ftgo/orderhistory/messaging/OrderHistoryEventHandlers.java"),
            repoRoot.resolve("delivery-service/src/main/java/net/ftgo/delivery/messaging/OrderEventConsumer.java"),
            repoRoot.resolve("accounting-service/src/main/java/net/ftgo/accounting/messaging/AccountingServiceCommandHandlers.java"),
            repoRoot.resolve("kitchen-service/src/main/java/net/ftgo/kitchen/messaging/KitchenServiceCommandHandlers.java")
        );

        List<String> forbiddenTokens = List.of(
            "net.ftgo.order.saga.commands.",
            "net.ftgo.kitchen.messaging.BeginCancelTicketCommand",
            "net.ftgo.kitchen.messaging.ConfirmCancelTicketCommand",
            "net.ftgo.kitchen.messaging.UndoCancelTicketCommand",
            "net.ftgo.kitchen.messaging.BeginReviseTicketCommand",
            "net.ftgo.kitchen.messaging.ConfirmReviseTicketCommand",
            "net.ftgo.kitchen.messaging.UndoReviseTicketCommand",
            "OrderCreatedEvent.class",
            "OrderApprovedEvent.class",
            "OrderCancelledEvent.class",
            "OrderRevisedEvent.class"
        );

        List<String> violations = new ArrayList<>();

        for (Path file : filesToCheck) {
            String content = Files.readString(file);
            for (String token : forbiddenTokens) {
                if (content.contains(token)) {
                    violations.add(file + " contains forbidden token: " + token);
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void localRestDtosCanRemainServiceLocal() throws Exception {
        Path repoRoot = findRepoRoot();
        Path createOrderRequest = repoRoot.resolve("order-service/src/main/java/net/ftgo/order/api/CreateOrderRequest.java");
        assertTrue(Files.exists(createOrderRequest), "Expected service-local REST DTO to remain outside common");
    }

    @Test
    void inProcessSagaDataCanRemainLocal() throws Exception {
        Path repoRoot = findRepoRoot();
        Path reviseSagaData = repoRoot.resolve("order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSagaData.java");
        assertTrue(Files.exists(reviseSagaData), "Expected in-process saga data class to remain service-local");
    }

    @Test
    void removedPackageLocalContractClassesDoNotReappearOutsideCommon() throws Exception {
        Path repoRoot = findRepoRoot();

        List<Path> forbiddenFilePaths = List.of(
            repoRoot.resolve("order-service/src/main/java/net/ftgo/order/saga/commands/ReverseAuthorizationCommand.java"),
            repoRoot.resolve("order-service/src/main/java/net/ftgo/order/saga/commands/ReviseAuthorizationCommand.java"),
            repoRoot.resolve("order-service/src/main/java/net/ftgo/order/saga/commands/BeginCancelTicketCommand.java"),
            repoRoot.resolve("order-service/src/main/java/net/ftgo/order/saga/commands/BeginReviseTicketCommand.java"),
            repoRoot.resolve("kitchen-service/src/main/java/net/ftgo/kitchen/messaging/BeginCancelTicketCommand.java"),
            repoRoot.resolve("kitchen-service/src/main/java/net/ftgo/kitchen/messaging/BeginReviseTicketCommand.java"),
            repoRoot.resolve("accounting-service/src/main/java/net/ftgo/accounting/messaging/ReverseAuthorizationCommand.java"),
            repoRoot.resolve("accounting-service/src/main/java/net/ftgo/accounting/messaging/ReviseAuthorizationCommand.java")
        );

        for (Path forbiddenPath : forbiddenFilePaths) {
            assertFalse(Files.exists(forbiddenPath), "Forbidden legacy contract class reappeared: " + forbiddenPath);
        }
    }

    private Path findRepoRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IOException("Could not find repository root (settings.gradle not found)");
        }
        return current;
    }
}
