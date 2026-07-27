package net.ftgo.consumer.api;

import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.consumer.config.SecurityConfiguration;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.security.ConsumerAuthorizationService;
import net.ftgo.consumer.service.ConsumerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "ftgo.consumer.registration-credit-limit=100.00",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    ConsumerController.class,
    ConsumerAuthorizationService.class
})
class ConsumerControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsumerService consumerService;

    @Test
    void permitsConsumerToReadOwnProfile() throws Exception {
        Consumer consumer = consumer(101L, "Alice", "alice@example.com", "100.00");
        when(consumerService.findConsumer(101L)).thenReturn(consumer);

        mockMvc.perform(get("/consumers/101")
                .with(authentication(consumerAuthentication(101L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(101L));
    }

    @Test
    void rejectsCrossConsumerProfileReadBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/consumers/202")
                .with(authentication(consumerAuthentication(101L))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Consumer access denied"));

        verifyNoInteractions(consumerService);
    }

    @Test
    void permitsConsumerToUpdateOwnProfile() throws Exception {
        Consumer updated = consumer(101L, "Alice Updated", "updated@example.com", "100.00");
        when(consumerService.updateConsumer(101L, "Alice Updated", "updated@example.com"))
            .thenReturn(updated);

        mockMvc.perform(put("/consumers/101")
                .with(authentication(consumerAuthentication(101L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Alice Updated",
                      "email": "updated@example.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alice Updated"));
    }

    @Test
    void registrationIgnoresCallerSuppliedCreditLimit() throws Exception {
        Consumer created = consumer(101L, "Alice", "alice@example.com", "100.00");
        when(consumerService.createConsumer(
            eq("Alice"),
            eq("alice@example.com"),
            any(Money.class)
        )).thenReturn(created);

        mockMvc.perform(post("/consumers")
                .with(authentication(consumerAuthentication(101L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Alice",
                      "email": "alice@example.com",
                      "creditLimit": 999999.00
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.creditLimit").value(100.00));

        verify(consumerService).createConsumer(
            "Alice",
            "alice@example.com",
            new Money("100.00")
        );
    }

    private Consumer consumer(
        Long id,
        String name,
        String email,
        String creditLimit
    ) {
        Consumer consumer = new Consumer(name, email, new Money(creditLimit));
        ReflectionTestUtils.setField(consumer, "id", id);
        return consumer;
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
