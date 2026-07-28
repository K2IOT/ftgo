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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    ConsumerAdminController.class,
    ConsumerAuthorizationService.class
})
class ConsumerAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsumerService consumerService;

    @Test
    void rejectsConsumerCreditLimitMutation() throws Exception {
        mockMvc.perform(put("/admin/consumers/101/credit-limit")
                .with(authentication(ftgoAuthentication("consumer-user", "CONSUMER", 101L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"creditLimit": 500.00}
                    """))
            .andExpect(status().isForbidden());

        verifyNoInteractions(consumerService);
    }

    @Test
    void permitsAdminCreditLimitMutation() throws Exception {
        Consumer updated = new Consumer(
            "Alice",
            "alice@example.com",
            new Money("500.00")
        );
        ReflectionTestUtils.setField(updated, "id", 101L);
        when(consumerService.updateCreditLimit(101L, new Money("500.00")))
            .thenReturn(updated);

        mockMvc.perform(put("/admin/consumers/101/credit-limit")
                .with(authentication(ftgoAuthentication("admin-user", "ADMIN", null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"creditLimit": 500.00}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creditLimit").value(500.00));

        verify(consumerService).updateCreditLimit(101L, new Money("500.00"));
    }

    private AbstractAuthenticationToken ftgoAuthentication(
        String subject,
        String role,
        Long consumerId
    ) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt.Builder builder = Jwt.withTokenValue(subject + "-token")
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of(role));
        if (consumerId != null) {
            builder.claim("consumer_id", consumerId);
        }
        return new FtgoJwtAuthenticationConverter("").convert(builder.build());
    }
}
