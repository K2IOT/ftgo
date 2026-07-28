package net.ftgo.consumer.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
    }
)
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    SecuritySmokeTest.ProbeController.class
})
class SecuritySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsDirectConsumerRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/consumers/security-probe"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAuthenticatedUserWithoutConsumerRole() throws Exception {
        mockMvc.perform(get("/consumers/security-probe").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_COURIER"),
                new SimpleGrantedAuthority("AUD_ftgo-api")
            )))
            .andExpect(status().isForbidden());
    }

    @Test
    void permitsConsumerTokenForConsumerEndpoint() throws Exception {
        mockMvc.perform(get("/consumers/security-probe").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CONSUMER"),
                new SimpleGrantedAuthority("AUD_ftgo-api")
            )))
            .andExpect(status().isOk());
    }

    @Test
    void internalEndpointRequiresServiceRoleAndInternalAudience() throws Exception {
        mockMvc.perform(get("/internal/security-probe").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_SERVICE"),
                new SimpleGrantedAuthority("AUD_ftgo-api")
            )))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/security-probe").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_SERVICE"),
                new SimpleGrantedAuthority("AUD_ftgo-internal")
            )))
            .andExpect(status().isOk());
    }

    @RestController
    public static class ProbeController {

        @GetMapping("/consumers/security-probe")
        String consumerProbe() {
            return "ok";
        }

        @GetMapping("/internal/security-probe")
        String internalProbe() {
            return "ok";
        }
    }
}
