package net.ftgo.accounting.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only control surface for deterministic provider operation accounting. */
@RestController
@RequestMapping("/admin/payments/provider-sandbox")
@ConditionalOnProperty(
    name = "ftgo.accounting.sandbox-provider-admin-enabled",
    havingValue = "true"
)
public class PaymentProviderSandboxAdminController {

    private final ConfigurablePaymentAuthorizationGateway provider;

    public PaymentProviderSandboxAdminController(
        ConfigurablePaymentAuthorizationGateway provider
    ) {
        this.provider = provider;
    }

    @GetMapping("/operations")
    public Map<String, Long> operationCounts() {
        return provider.getOperationCounts();
    }

    @DeleteMapping
    public ResponseEntity<Void> reset() {
        provider.resetSandboxLedger();
        return ResponseEntity.noContent().build();
    }
}
