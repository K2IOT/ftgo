package net.ftgo.accounting.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/** Parses provider=secret pairs without ever logging the configured values. */
@Component
public class ConfigurablePaymentWebhookSecretResolver implements PaymentWebhookSecretResolver {

    private final Map<String, String> secrets;

    public ConfigurablePaymentWebhookSecretResolver(
        @Value("${ftgo.accounting.payment-webhook-secrets:sandbox=change-me}") String configuredSecrets
    ) {
        this.secrets = Arrays.stream(configuredSecrets.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.split("=", 2))
            .filter(parts -> parts.length == 2
                && !parts[0].isBlank()
                && !parts[1].isBlank())
            .collect(Collectors.toUnmodifiableMap(
                parts -> parts[0].trim(),
                parts -> parts[1].trim(),
                (first, replacement) -> replacement
            ));
    }

    @Override
    public String resolveSecret(String provider) {
        String secret = secrets.get(provider);
        if (secret == null || secret.isBlank()) {
            throw new InvalidPaymentWebhookException(
                "No payment webhook secret configured for provider " + provider);
        }
        return secret;
    }
}
