package net.ftgo.delivery.api;

import net.ftgo.common.Address;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.delivery.config.SecurityConfiguration;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.messaging.DomainEventPublisher;
import net.ftgo.delivery.repository.DeliveryRepository;
import net.ftgo.delivery.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    DeliveryController.class
})
class DeliveryControllerIdentityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService deliveryService;

    @MockBean
    private DeliveryRepository deliveryRepository;

    @MockBean
    private DomainEventPublisher eventPublisher;

    @Test
    void claimsDeliveryWithoutCourierIdInRequestBody() throws Exception {
        Delivery claimed = assignedDelivery(77L);
        when(deliveryService.claimDelivery(eq(1L), any(Authentication.class)))
            .thenReturn(claimed);

        mockMvc.perform(post("/deliveries/1/assign")
                .with(authentication(courierAuthentication(77L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierId").value(77L));

        verify(deliveryService).claimDelivery(eq(1L), any(Authentication.class));
        verifyNoInteractions(deliveryRepository, eventPublisher);
    }

    @Test
    void ignoresSpoofedCourierIdAndUsesAuthenticatedCourier() throws Exception {
        Delivery claimed = assignedDelivery(77L);
        when(deliveryService.claimDelivery(eq(1L), any(Authentication.class)))
            .thenReturn(claimed);

        mockMvc.perform(post("/deliveries/1/assign")
                .with(authentication(courierAuthentication(77L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"courierId": 999}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierId").value(77L));

        verify(deliveryService).claimDelivery(eq(1L), any(Authentication.class));
        verifyNoInteractions(deliveryRepository, eventPublisher);
    }

    private Delivery assignedDelivery(Long courierId) {
        Delivery delivery = new Delivery(
            123L,
            new Address("1 Pickup St", "Austin", "TX", "78701"),
            new Address("2 Dropoff St", "Austin", "TX", "78702"),
            LocalDateTime.now().plusHours(1)
        );
        ReflectionTestUtils.setField(delivery, "id", 1L);
        ReflectionTestUtils.setField(delivery, "version", 0L);
        delivery.assignCourier(courierId);
        return delivery;
    }

    private AbstractAuthenticationToken courierAuthentication(Long courierId) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("courier-token")
            .header("alg", "RS256")
            .subject("courier-user")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of("COURIER"))
            .claim("courier_id", courierId)
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
