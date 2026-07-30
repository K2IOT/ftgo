package net.ftgo.kitchen.api;

import net.ftgo.common.web.CorrelationIdFilter;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.kitchen.security.TicketAuthorizationService;
import net.ftgo.kitchen.service.KitchenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KitchenProblemDetailContractTest {

    private KitchenService kitchenService;
    private Authentication authentication;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        kitchenService = mock(KitchenService.class);
        authentication = mock(Authentication.class);
        KitchenController controller = new KitchenController(
            mock(TicketRepository.class),
            kitchenService,
            mock(TicketAuthorizationService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilters(new CorrelationIdFilter())
            .build();
    }

    @Test
    void ticketConflictUsesStableCorrelatedProblemDetailWithoutLeakingMessage() throws Exception {
        when(kitchenService.acceptTicket(eq(42L), eq(authentication)))
            .thenThrow(new IllegalStateException("database state and payment token detail"));

        mockMvc.perform(post("/tickets/42/accept")
                .principal(authentication)
                .header(CorrelationIdFilter.HEADER_NAME, "corr-kitchen-1234"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "corr-kitchen-1234"))
            .andExpect(jsonPath("$.type").value("https://ftgo.example/problems/kitchen-ticket-conflict"))
            .andExpect(jsonPath("$.title").value("Ticket state conflict"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("Ticket cannot be changed from its current state"))
            .andExpect(jsonPath("$.instance").value("/tickets/42/accept"))
            .andExpect(jsonPath("$.errorCode").value("KITCHEN_TICKET_CONFLICT"))
            .andExpect(jsonPath("$.correlationId").value("corr-kitchen-1234"))
            .andExpect(content().string(not(containsString("payment token"))));
    }
}
