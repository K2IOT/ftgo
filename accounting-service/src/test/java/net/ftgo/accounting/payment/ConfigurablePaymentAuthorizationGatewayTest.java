package net.ftgo.accounting.payment;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurablePaymentAuthorizationGatewayTest {

    private final ConfigurablePaymentAuthorizationGateway gateway =
        new ConfigurablePaymentAuthorizationGateway("tok_decline,tok_risk");

    @Test
    void approvesTokensOutsideTheConfiguredDenyList() {
        assertThat(gateway.authorize("tok_ok", new Money("25.00")).approved()).isTrue();
    }

    @Test
    void deniesTokensConfiguredByOperations() {
        PaymentAuthorizationDecision decision =
            gateway.authorize("tok_decline", new Money("25.00"));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason())
            .isEqualTo(ConfigurablePaymentAuthorizationGateway.PROVIDER_DECLINED);
    }

    @Test
    void deniesBlankTokensFromNewProducers() {
        PaymentAuthorizationDecision decision = gateway.authorize(" ", new Money("25.00"));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason())
            .isEqualTo(ConfigurablePaymentAuthorizationGateway.TOKEN_REQUIRED);
    }

    @Test
    void acceptsNullTokenFromLegacyInFlightCommands() {
        assertThat(gateway.authorize(null, new Money("25.00")).approved()).isTrue();
    }
}
