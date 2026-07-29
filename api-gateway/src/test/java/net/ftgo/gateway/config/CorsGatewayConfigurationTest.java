package net.ftgo.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsGatewayConfigurationTest {

    private final CorsGatewayConfiguration configuration = new CorsGatewayConfiguration();

    @Test
    void allowsConfiguredOriginInCredentialedMode() {
        GatewayCorsProperties properties = properties(List.of("https://app.example"), true);

        CorsConfiguration cors = configuration.corsConfiguration(properties);

        assertEquals("https://app.example", cors.checkOrigin("https://app.example"));
        assertTrue(cors.getAllowCredentials());
        assertFalse(cors.getAllowedOrigins().contains("*"));
    }

    @Test
    void rejectsUnlistedOrigin() {
        GatewayCorsProperties properties = properties(List.of("https://app.example"), true);

        CorsConfiguration cors = configuration.corsConfiguration(properties);

        assertNull(cors.checkOrigin("https://evil.example"));
    }

    @Test
    void defaultsToEmptyAllowlistWithoutCredentials() {
        GatewayCorsProperties properties = new GatewayCorsProperties();

        CorsConfiguration cors = configuration.corsConfiguration(properties);

        assertEquals(List.of(), cors.getAllowedOrigins());
        assertFalse(cors.getAllowCredentials());
        assertNull(cors.checkOrigin("https://app.example"));
    }

    @Test
    void rejectsCredentialedWildcardConfiguration() {
        GatewayCorsProperties properties = properties(List.of("*"), true);

        assertThrows(
            IllegalStateException.class,
            () -> configuration.corsConfiguration(properties)
        );
    }

    private GatewayCorsProperties properties(List<String> allowedOrigins, boolean allowCredentials) {
        GatewayCorsProperties properties = new GatewayCorsProperties();
        properties.setAllowedOrigins(allowedOrigins);
        properties.setAllowCredentials(allowCredentials);
        return properties;
    }
}
