package net.ftgo.kitchen.api;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.kitchen.config.SecurityConfiguration;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.kitchen.security.TicketAuthorizationService;
import net.ftgo.kitchen.service.KitchenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    KitchenController.class,
    TicketAuthorizationService.class
})
class KitchenControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketRepository ticketRepository;

    @MockitoBean
    private KitchenService kitchenService;

    @Test
    void rejectsCrossRestaurantTicketQueryBeforeRepositoryAccess() throws Exception {
        mockMvc.perform(get("/tickets")
                .queryParam("restaurantId", "20")
                .with(authentication(restaurantAuthentication(10L))))
            .andExpect(status().isForbidden());

        verifyNoInteractions(ticketRepository, kitchenService);
    }

    @Test
    void preservesApplicationServiceOwnershipDenialAsForbidden() throws Exception {
        when(kitchenService.acceptTicket(eq(1L), any(Authentication.class)))
            .thenThrow(new AccessDeniedException("Ticket access denied"));

        mockMvc.perform(post("/tickets/1/accept")
                .with(authentication(restaurantAuthentication(10L))))
            .andExpect(status().isForbidden());

        verifyNoInteractions(ticketRepository);
    }

    private AbstractAuthenticationToken restaurantAuthentication(Long restaurantId) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("restaurant-token")
            .header("alg", "RS256")
            .subject("restaurant-user")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of("RESTAURANT"))
            .claim("restaurant_ids", List.of(restaurantId))
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
