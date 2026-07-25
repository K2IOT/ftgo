package net.ftgo.accounting.webhook;

@FunctionalInterface
public interface PaymentWebhookSecretResolver {

    String resolveSecret(String provider);
}
