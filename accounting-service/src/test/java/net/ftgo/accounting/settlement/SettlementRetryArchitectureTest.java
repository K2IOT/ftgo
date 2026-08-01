package net.ftgo.accounting.settlement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementRetryArchitectureTest {

    @Test
    void accountingProductionCodeContainsNoBlockingSleepOrRetryingGateway() throws IOException {
        Path sourceRoot = locateSourceRoot();
        Path retryingGateway = sourceRoot.resolve(
            "net/ftgo/accounting/settlement/RetryingSettlementGateway.java"
        );

        assertThat(retryingGateway).doesNotExist();

        List<Path> blockingSleepOffenders;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            blockingSleepOffenders = files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> contains(path, "Thread.sleep("))
                .toList();
        }

        assertThat(blockingSleepOffenders)
            .as("Accounting production code must never block a consumer thread with Thread.sleep")
            .isEmpty();
    }

    private static Path locateSourceRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path moduleSource = workingDirectory.resolve("src/main/java");
        if (Files.isDirectory(moduleSource)) {
            return moduleSource;
        }

        Path repositorySource = workingDirectory.resolve("accounting-service/src/main/java");
        if (Files.isDirectory(repositorySource)) {
            return repositorySource;
        }

        throw new IllegalStateException(
            "Cannot locate accounting-service production sources from " + workingDirectory
        );
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }
}
