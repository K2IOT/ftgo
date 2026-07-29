package net.ftgo.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties(GatewayCorsProperties.class)
public class CorsGatewayConfiguration {

    @Bean
    public CorsConfiguration corsConfiguration(GatewayCorsProperties properties) {
        List<String> allowedOrigins = normalize(properties.getAllowedOrigins());
        if (properties.isAllowCredentials() && allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                "Credentialed CORS requires an explicit origin allowlist"
            );
        }

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(normalize(properties.getAllowedMethods()));
        cors.setAllowedHeaders(normalize(properties.getAllowedHeaders()));
        cors.setExposedHeaders(normalize(properties.getExposedHeaders()));
        cors.setAllowCredentials(properties.isAllowCredentials());
        cors.setMaxAge(properties.getMaxAge().toSeconds());
        return cors;
    }

    @Bean
    public CorsWebFilter corsWebFilter(CorsConfiguration corsConfiguration) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsWebFilter(source);
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }
}
