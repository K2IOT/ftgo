package net.ftgo.common.web;

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

class ProblemDetailContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();
    }

    @Test
    void returnsRfc9457ShapeAndPreservesSafeCorrelationId() throws Exception {
        mockMvc.perform(get("/problem/invalid")
                .header(CorrelationIdFilter.HEADER_NAME, "req-12345678"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "req-12345678"))
            .andExpect(jsonPath("$.type").value("https://ftgo.example/problems/invalid-request"))
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Invalid request"))
            .andExpect(jsonPath("$.instance").value("/problem/invalid"))
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.correlationId").value("req-12345678"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternalMessageOrExceptionClass() throws Exception {
        mockMvc.perform(get("/problem/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
            .andExpect(content().string(not(containsString("database-password"))))
            .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    void rejectsUnsafeCorrelationIdAndGeneratesNewValue() throws Exception {
        mockMvc.perform(get("/problem/invalid")
                .header(CorrelationIdFilter.HEADER_NAME, "unsafe value with spaces"))
            .andExpect(status().isBadRequest())
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, not("unsafe value with spaces")))
            .andExpect(jsonPath("$.correlationId", not("unsafe value with spaces")));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/problem/invalid")
        void invalid() {
            throw new IllegalArgumentException("client supplied secret detail");
        }

        @GetMapping("/problem/unexpected")
        void unexpected() {
            throw new RuntimeException("database-password");
        }
    }
}
