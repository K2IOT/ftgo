package net.ftgo.orderhistory.api;

import net.ftgo.common.web.CorrelationIdFilter;
import net.ftgo.orderhistory.service.UnsupportedOrderHistoryQueryException;
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

class OrderHistoryProblemDetailContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new OrderHistoryExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();
    }

    @Test
    void invalidPagingTokenUsesStableProblemDetailWithoutLeakingParserMessage() throws Exception {
        mockMvc.perform(get("/order-history/problem")
                .header(CorrelationIdFilter.HEADER_NAME, "corr-history-1234"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "corr-history-1234"))
            .andExpect(jsonPath("$.type").value("https://ftgo.example/problems/order-history-query-invalid"))
            .andExpect(jsonPath("$.title").value("Invalid order history query"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("The order history query is invalid"))
            .andExpect(jsonPath("$.instance").value("/order-history/problem"))
            .andExpect(jsonPath("$.errorCode").value("ORDER_HISTORY_QUERY_INVALID"))
            .andExpect(jsonPath("$.correlationId").value("corr-history-1234"))
            .andExpect(content().string(not(containsString("HMAC secret"))));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/order-history/problem")
        void invalid() {
            throw new UnsupportedOrderHistoryQueryException(
                "HMAC secret mismatch in parser",
                "ORDER_HISTORY_QUERY_INVALID"
            );
        }
    }
}
