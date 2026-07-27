package net.ftgo.restaurant.api;

import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.restaurant.config.SecurityConfiguration;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.security.RestaurantAuthorizationService;
import net.ftgo.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    RestaurantController.class,
    RestaurantAuthorizationService.class
})
class RestaurantControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestaurantService restaurantService;

    @Test
    void rejectsMenuMutationForAnotherRestaurantBeforeCallingService() throws Exception {
        when(restaurantService.createMenuItem(eq(20L), any(MenuItem.class)))
            .thenReturn(new MenuItem(20L, "Burger", "Fresh", new Money("12.00")));

        mockMvc.perform(post("/restaurants/20/menu-items")
                .with(authentication(ftgoAuthentication("RESTAURANT", List.of(10L))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Burger",
                      "description": "Fresh",
                      "price": {"amount": 12.00}
                    }
                    """))
            .andExpect(status().isForbidden());

        verifyNoInteractions(restaurantService);
    }

    @Test
    void rejectsRestaurantCreationForRestaurantPrincipal() throws Exception {
        when(restaurantService.createRestaurant(any(Restaurant.class)))
            .thenReturn(new Restaurant(
                "New Restaurant",
                new Address("1 Main St", "Austin", "TX", "78701"),
                "{}"
            ));

        mockMvc.perform(post("/restaurants")
                .with(authentication(ftgoAuthentication("RESTAURANT", List.of(10L))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "New Restaurant",
                      "address": {
                        "street": "1 Main St",
                        "city": "Austin",
                        "state": "TX",
                        "zipCode": "78701"
                      },
                      "openingHours": "{}"
                    }
                    """))
            .andExpect(status().isForbidden());

        verifyNoInteractions(restaurantService);
    }

    private AbstractAuthenticationToken ftgoAuthentication(
        String role,
        List<Long> restaurantIds
    ) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue(role.toLowerCase() + "-token")
            .header("alg", "RS256")
            .subject(role.toLowerCase() + "-user")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of(role))
            .claim("restaurant_ids", restaurantIds)
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
