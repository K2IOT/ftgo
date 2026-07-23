package net.ftgo.accounting.payment;

import net.ftgo.common.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic payment-provider adapter used until a real provider integration
 * is configured. Operations can inject a comma-separated deny list without
 * changing order or saga code.
 */
@Component
public class ConfigurablePaymentAuthorizationGateway implements PaymentAuthorizationGateway {

    public static final String PROVIDER_DECLINED = "PAYMENT_PROVIDER_DECLINED";
    public static final String TOKEN_REQUIRED = "PAYMENT_TOKEN_REQUIRED";

    private final Set<String> declinedTokens;

    public ConfigurablePaymentAuthorizationGateway(
        @Value("${ftgo.accounting.declined-payment-tokens:}") String declinedPaymentTokens
    ) {
        this.declinedTokens = Arrays.stream(declinedPaymentTokens.split(","))
            .map(String::trim)
            .filter(token -> !token.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public PaymentAuthorizationDecision authorize(String paymentToken, Money amount) {
        // Commands produced before the rolling upgrade did not carry a token.
        if (paymentToken == null) {
            return PaymentAuthorizationDecision.approved();
        }
        if (paymentToken.isBlank()) {
            return PaymentAuthorizationDecision.denied(TOKEN_REQUIRED);
        }
        if (declinedTokens.contains(paymentToken)) {
            return PaymentAuthorizationDecision.denied(PROVIDER_DECLINED);
        }
        return PaymentAuthorizationDecision.approved();
    }
}
