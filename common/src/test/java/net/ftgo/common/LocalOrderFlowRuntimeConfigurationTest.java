package net.ftgo.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOrderFlowRuntimeConfigurationTest {

    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().getParent();
    private static final Pattern JDBC_URL = Pattern.compile("jdbc:mysql://localhost:(\\d+)/");
    private static final Pattern COMPOSE_PORT = Pattern.compile("\"(\\d+):3306\"");
    private static final Pattern NEXT_COMPOSE_SERVICE = Pattern.compile("(?m)^  [A-Za-z0-9_-]+:");

    @Test
    void localProfilesUseThePortsExposedByDockerCompose() throws IOException {
        Map<String, String> expectedPortsByService = new LinkedHashMap<>();
        expectedPortsByService.put("order-service", exposedMysqlPort("mysql-order"));
        expectedPortsByService.put("consumer-service", exposedMysqlPort("mysql-consumer"));
        expectedPortsByService.put("restaurant-service", exposedMysqlPort("mysql-restaurant"));
        expectedPortsByService.put("kitchen-service", exposedMysqlPort("mysql-kitchen"));
        expectedPortsByService.put("accounting-service", exposedMysqlPort("mysql-accounting"));
        expectedPortsByService.put("delivery-service", exposedMysqlPort("mysql-delivery"));

        Map<String, String> actualPortsByService = new LinkedHashMap<>();
        for (String service : expectedPortsByService.keySet()) {
            actualPortsByService.put(service, configuredLocalMysqlPort(service));
        }

        assertThat(actualPortsByService).isEqualTo(expectedPortsByService);
    }

    private String configuredLocalMysqlPort(String service) throws IOException {
        Path localProfile = REPO_ROOT.resolve(service).resolve("src/main/resources/application-local.yml");
        String applicationYaml = Files.readString(localProfile);
        Matcher matcher = JDBC_URL.matcher(applicationYaml);
        assertThat(matcher.find())
            .as("JDBC URL in %s application-local.yml", service)
            .isTrue();
        return matcher.group(1);
    }

    private String exposedMysqlPort(String composeService) throws IOException {
        String compose = Files.readString(REPO_ROOT.resolve("deployment/docker-compose.infra.yml"));
        int serviceIndex = compose.indexOf("  " + composeService + ":");
        assertThat(serviceIndex)
            .as("Docker Compose service %s exists", composeService)
            .isGreaterThanOrEqualTo(0);

        Matcher nextServiceMatcher = NEXT_COMPOSE_SERVICE.matcher(compose);
        int nextServiceIndex = nextServiceMatcher.find(serviceIndex + composeService.length() + 3)
            ? nextServiceMatcher.start()
            : -1;
        String serviceBlock = nextServiceIndex >= 0
            ? compose.substring(serviceIndex, nextServiceIndex)
            : compose.substring(serviceIndex);

        Matcher matcher = COMPOSE_PORT.matcher(serviceBlock);
        assertThat(matcher.find())
            .as("host MySQL port for Docker Compose service %s", composeService)
            .isTrue();
        return matcher.group(1);
    }
}
