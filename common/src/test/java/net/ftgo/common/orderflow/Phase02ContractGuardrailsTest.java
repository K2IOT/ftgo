package net.ftgo.common.orderflow;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.CommitConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.RefundPaymentCommand;
import net.ftgo.common.orderflow.commands.ReleaseConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ReserveConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase02ContractGuardrailsTest {

    @Test
    void everyNewParticipantCommandImplementsEventuateCommand() {
        List<Class<?>> commands = List.of(
            ValidateOrderMenuCommand.class,
            ReserveConsumerCreditCommand.class,
            CommitConsumerCreditCommand.class,
            ReleaseConsumerCreditCommand.class,
            CaptureAuthorizationCommand.class,
            VoidAuthorizationCommand.class,
            RefundPaymentCommand.class
        );
        commands.forEach(type -> assertTrue(Command.class.isAssignableFrom(type), type.getName()));
    }

    @Test
    void participantModulesDoNotDeclarePhase02WireContractsLocally() {
        Path root = findRepoRoot();
        List<String> names = List.of(
            "ValidateOrderMenuCommand.java",
            "ReserveConsumerCreditCommand.java",
            "CommitConsumerCreditCommand.java",
            "ReleaseConsumerCreditCommand.java",
            "CaptureAuthorizationCommand.java",
            "VoidAuthorizationCommand.java",
            "RefundPaymentCommand.java"
        );
        List<Path> modules = List.of(
            root.resolve("order-service/src/main/java"),
            root.resolve("restaurant-service/src/main/java"),
            root.resolve("consumer-service/src/main/java"),
            root.resolve("accounting-service/src/main/java")
        );
        for (Path module : modules) {
            for (String name : names) {
                assertFalse(findFile(module, name), "Duplicate service-local contract: " + module + "/" + name);
            }
        }
    }

    private boolean findFile(Path root, String fileName) {
        if (!Files.exists(root)) {
            return false;
        }
        try (var paths = Files.walk(root)) {
            return paths.anyMatch(path -> path.getFileName().toString().equals(fileName));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to scan " + root, e);
        }
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
