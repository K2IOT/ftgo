package net.ftgo.accounting.api.admin;

import net.ftgo.common.web.CorrelationIdFilter;
import net.ftgo.accounting.settlement.SettlementGatewayTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentSettlementProblemDetailContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new PaymentSettlementExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();
    }

    @Test
    void settlementConflictUsesStableProblemDetailWithoutLeakingState() throws Exception {
        mockMvc.perform(get("/api/admin/payment-settlement/conflict")
                .header(CorrelationIdFilter.HEADER_NAME, "corr-payment-1234"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "corr-payment-1234"))
            .andExpect(jsonPath("$.type").value("https://ftgo.example/problems/payment-settlement-conflict"))
            .andExpect(jsonPath("$.title").value("Payment settlement conflict"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("The payment settlement request conflicts with current state"))
            .andExpect(jsonPath("$.instance").value("/api/admin/payment-settlement/conflict"))
            .andExpect(jsonPath("$.errorCode").value("PAYMENT_SETTLEMENT_CONFLICT"))
            .andExpect(jsonPath("$.correlationId").value("corr-payment-1234"))
            .andExpect(content().string(not(containsString("provider ledger secret"))));
    }

    @Test
    void providerTimeoutUsesStableServiceUnavailableProblem() throws Exception {
        mockMvc.perform(get("/api/admin/payment-settlement/provider-timeout")
                .header(CorrelationIdFilter.HEADER_NAME, "corr-provider-1234"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("PAYMENT_PROVIDER_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail").value("The payment provider is temporarily unavailable"))
            .andExpect(content().string(not(containsString("provider timeout secret"))));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/admin/payment-settlement/conflict")
        void conflict() {
            throw new IllegalStateException("provider ledger secret state");
        }

        @GetMapping("/api/admin/payment-settlement/provider-timeout")
        void providerTimeout() {
            throw new SettlementGatewayTimeoutException("provider timeout secret");
        }
    }
}
