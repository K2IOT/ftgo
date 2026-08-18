package net.ftgo.consumer.api;

import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.consumer.config.EventuateTramTestConfiguration;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.repository.ConsumerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration tests for ConsumerController REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(EventuateTramTestConfiguration.class)
class ConsumerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConsumerRepository consumerRepository;

    @BeforeEach
    void setUp() {
        consumerRepository.deleteAll();
    }

    @Test
    void testCreateConsumerUsesServerCreditPolicy() throws Exception {
        String requestBody = """
            {
                "name": "John Doe",
                "email": "john.doe@example.com",
                "creditLimit": 999999.00
            }
            """;

        mockMvc.perform(post("/consumers")
                .with(authentication(consumerAuthentication(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.name", is("John Doe")))
            .andExpect(jsonPath("$.email", is("john.doe@example.com")))
            .andExpect(jsonPath("$.creditLimit", is(100.00)))
            .andExpect(jsonPath("$.availableCredit", is(100.00)));
    }

    @Test
    void testCreateConsumerWithDuplicateEmail() throws Exception {
        Consumer consumer = new Consumer("Jane Doe", "jane@example.com", new Money("100.00"));
        consumerRepository.save(consumer);

        String requestBody = """
            {
                "name": "Jane Smith",
                "email": "jane@example.com",
                "creditLimit": 200.00
            }
            """;

        mockMvc.perform(post("/consumers")
                .with(authentication(consumerAuthentication(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("already exists")));
    }

    @Test
    void testGetConsumer() throws Exception {
        Consumer consumer = new Consumer("Alice Smith", "alice@example.com", new Money("150.00"));
        consumer = consumerRepository.save(consumer);

        mockMvc.perform(get("/consumers/" + consumer.getId())
                .with(authentication(consumerAuthentication(consumer.getId()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(consumer.getId().intValue())))
            .andExpect(jsonPath("$.name", is("Alice Smith")))
            .andExpect(jsonPath("$.email", is("alice@example.com")))
            .andExpect(jsonPath("$.creditLimit", is(150.00)));
    }

    @Test
    void testGetConsumerNotFound() throws Exception {
        mockMvc.perform(get("/consumers/999")
                .with(authentication(consumerAuthentication(999L))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("not found")));
    }

    @Test
    void testUpdateConsumer() throws Exception {
        Consumer consumer = new Consumer("Bob Johnson", "bob@example.com", new Money("200.00"));
        consumer = consumerRepository.save(consumer);

        String requestBody = """
            {
                "name": "Robert Johnson",
                "email": "robert@example.com"
            }
            """;

        mockMvc.perform(put("/consumers/" + consumer.getId())
                .with(authentication(consumerAuthentication(consumer.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(consumer.getId().intValue())))
            .andExpect(jsonPath("$.name", is("Robert Johnson")))
            .andExpect(jsonPath("$.email", is("robert@example.com")))
            .andExpect(jsonPath("$.creditLimit", is(200.00)));
    }

    @Test
    void testUpdateConsumerNotFound() throws Exception {
        String requestBody = """
            {
                "name": "Test User",
                "email": "test@example.com"
            }
            """;

        mockMvc.perform(put("/consumers/999")
                .with(authentication(consumerAuthentication(999L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("not found")));
    }

    @Test
    void testCreateConsumerWithInvalidEmail() throws Exception {
        String requestBody = """
            {
                "name": "Invalid User",
                "email": "invalid-email"
            }
            """;

        mockMvc.perform(post("/consumers")
                .with(authentication(consumerAuthentication(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void callerSuppliedNegativeCreditLimitIsIgnored() throws Exception {
        String requestBody = """
            {
                "name": "Test User",
                "email": "test@example.com",
                "creditLimit": -50.00
            }
            """;

        mockMvc.perform(post("/consumers")
                .with(authentication(consumerAuthentication(1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.creditLimit", is(100.00)));
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
