package net.ftgo.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ftgo.gateway.cors")
public class GatewayCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();
    private List<String> allowedMethods = new ArrayList<>(List.of(
        "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
    ));
    private List<String> allowedHeaders = new ArrayList<>(List.of(
        "Authorization",
        "Content-Type",
        "Accept",
        "Origin",
        "X-Requested-With",
        "X-Request-Id"
    ));
    private List<String> exposedHeaders = new ArrayList<>(List.of(
        "X-Request-Id",
        "X-RateLimit-Remaining",
        "X-RateLimit-Limit"
    ));
    private boolean allowCredentials;
    private Duration maxAge = Duration.ofHours(1);

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = mutableCopy(allowedOrigins);
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = mutableCopy(allowedMethods);
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = mutableCopy(allowedHeaders);
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = mutableCopy(exposedHeaders);
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
    }

    private List<String> mutableCopy(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
