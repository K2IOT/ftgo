package net.ftgo.accounting.payment;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProviderSandboxAdminControllerTest {

    @Test
    void exposesAndResetsDeterministicOperationCounts() {
        ConfigurablePaymentAuthorizationGateway provider =
            new ConfigurablePaymentAuthorizationGateway("");
        provider.authorize("tok_ok", new net.ftgo.common.Money("12.00"), "authorize-admin");
        PaymentProviderSandboxAdminController controller =
            new PaymentProviderSandboxAdminController(provider);

        assertThat(controller.operationCounts()).containsEntry("authorize", 1L);
        controller.reset();
        assertThat(controller.operationCounts()).isEqualTo(Map.of());
    }
}
