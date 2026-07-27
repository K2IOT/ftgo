package net.ftgo.order.api;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.order.config.SecurityConfiguration;
import net.ftgo.order.service.OrderService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "ftgo.features.phase-02-enabled=true",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    OrderController.class
})
class OrderControllerIdentityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void derivesConsumerIdentityFromAuthenticatedPrincipal() throws Exception {
        when(orderService.createOrder(
            eq(101L),
            eq(501L),
            eq(7L),
            anyList(),
            any(),
            any()
        )).thenReturn(9001L);

        mockMvc.perform(post("/orders")
                .with(authentication(consumerAuthentication(101L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "consumerId": 999,
                      "restaurantId": 501,
                      "expectedMenuVersion": 7,
                      "lineItems": [
                        {
                          "menuItemId": 10,
                          "name": "Burger",
                          "price": {"amount": 12.99},
                          "quantity": 1
                        }
                      ],
                      "deliveryAddress": {
                        "street": "123 Main St",
                        "city": "San Francisco",
                        "state": "CA",
                        "zipCode": "94102"
                      },
                      "deliveryTime": "2030-01-01T12:00:00",
                      "paymentToken": "tok_test"
                    }
                    """))
            .andExpect(status().isCreated());

        verify(orderService).createOrder(
            eq(101L),
            eq(501L),
            eq(7L),
            anyList(),
            any(),
            any()
        );
    }

    private AbstractAuthenticationToken consumerAuthentication(Long consumerId) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("consumer-token")
            .header("alg", "RS256")
            .subject("consumer-" + consumerId)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("consumer_id", consumerId)
            .claim("roles", List.of("CONSUMER"))
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
