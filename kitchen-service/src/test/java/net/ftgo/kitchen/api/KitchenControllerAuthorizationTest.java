package net.ftgo.kitchen.api;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.kitchen.config.SecurityConfiguration;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.kitchen.security.TicketAuthorizationService;
import net.ftgo.kitchen.service.KitchenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
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

@WebMvcTest(
    controllers = KitchenController.class,
    properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
    }
)
@Import({SecurityConfiguration.class, TicketAuthorizationService.class})
class KitchenControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TicketRepository ticketRepository;

    @MockBean
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
    void rejectsCrossRestaurantMutationBeforeApplicationServiceCall() throws Exception {
        when(kitchenService.acceptTicket(eq(1L), any(Authentication.class)))
            .thenReturn(ticketForRestaurant(20L));

        mockMvc.perform(post("/tickets/1/accept")
                .with(authentication(restaurantAuthentication(10L))))
            .andExpect(status().isForbidden());

        verifyNoInteractions(ticketRepository, kitchenService);
    }

    private Ticket ticketForRestaurant(Long restaurantId) {
        return new Ticket(
            restaurantId,
            123L,
            List.of(new TicketLineItem(5L, "Burger", 1))
        );
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
