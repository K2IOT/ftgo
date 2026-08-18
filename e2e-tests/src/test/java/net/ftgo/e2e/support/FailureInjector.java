package net.ftgo.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class FailureInjector {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final String connectUrl;
    private final Path runDirectory;

    public FailureInjector(String connectUrl, Path runDirectory) {
        this.connectUrl = stripTrailingSlash(connectUrl);
        this.runDirectory = runDirectory;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public void pauseConnector(String connectorName) {
        connectorAction(connectorName, "pause");
        awaitConnectorState(connectorName, "PAUSED", Duration.ofSeconds(30));
    }

    public void resumeConnector(String connectorName) {
        connectorAction(connectorName, "resume");
        awaitConnectorState(connectorName, "RUNNING", Duration.ofSeconds(60));
    }

    public void restartService(String serviceName, String healthUrl) {
        Path restartScript = runDirectory.resolve("control")
            .resolve(serviceName + "-restart.sh");
        if (!Files.isExecutable(restartScript)) {
            throw new IllegalStateException("Restart script is not executable: " + restartScript);
        }
        runProcess(List.of("bash", restartScript.toString()), Duration.ofSeconds(30));
        awaitHealth(healthUrl, Duration.ofSeconds(120));
    }

    public void stopService(String serviceName) {
        Path pidFile = runDirectory.resolve("control").resolve(serviceName + ".pid");
        try {
            long pid = Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).trim());
            ProcessHandle.of(pid).ifPresent(process -> {
                process.destroy();
                try {
                    process.onExit().get();
                } catch (Exception e) {
                    process.destroyForcibly();
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to stop service " + serviceName, e);
        }
    }

    public void awaitHealth(String healthUrl, Duration timeout) {
        String probeUrl = readinessProbeUrl(healthUrl);
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(probeUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
                if (response.statusCode() == 200
                    && response.body().contains("\"status\":\"UP\"")) {
                    return;
                }
            } catch (Exception ignored) {
                // Restart windows are expected to reject connections temporarily.
            }
            sleep(Duration.ofMillis(500));
        }
        throw new AssertionError("Service did not become healthy: " + probeUrl);
    }

    private String readinessProbeUrl(String healthUrl) {
        if (healthUrl.endsWith("/actuator/health")) {
            return healthUrl + "/readiness";
        }
        return healthUrl;
    }

    private void connectorAction(String connectorName, String action) {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(connectUrl + "/connectors/" + connectorName + "/" + action)
            )
            .timeout(Duration.ofSeconds(10))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
        try {
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                    "Kafka Connect action failed: " + response.statusCode() + " " + response.body()
                );
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to invoke connector action", e);
        }
    }

    private void awaitConnectorState(
        String connectorName,
        String expectedState,
        Duration timeout
    ) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(
                            URI.create(connectUrl + "/connectors/" + connectorName + "/status")
                        )
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
                if (response.statusCode() == 200) {
                    JsonNode status = JSON.readTree(response.body());
                    boolean connectorMatches = expectedState.equals(
                        status.path("connector").path("state").asText()
                    );
                    boolean tasksMatch = true;
                    for (JsonNode task : status.path("tasks")) {
                        String taskState = task.path("state").asText();
                        if (!expectedState.equals(taskState)
                            && !("PAUSED".equals(expectedState) && "RUNNING".equals(taskState))) {
                            tasksMatch = false;
                        }
                    }
                    if (connectorMatches && tasksMatch) {
                        return;
                    }
                }
            } catch (Exception ignored) {
                // Poll until the connector reaches the requested state.
            }
            sleep(Duration.ofMillis(500));
        }
        throw new AssertionError(
            "Connector " + connectorName + " did not reach " + expectedState
        );
    }

    private void runProcess(List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Process timed out: " + String.join(" ", command));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                    "Process failed: " + String.join(" ", command) + "\n" + output
                );
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to run failure injection command", e);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", e);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
