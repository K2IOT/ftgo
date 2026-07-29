package net.ftgo.orderhistory.api;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.orderhistory.config.SecurityConfiguration;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.security.OrderHistoryAuthorizationService;
import net.ftgo.orderhistory.service.OrderHistoryPageCursor;
import net.ftgo.orderhistory.service.OrderHistoryPagingTokenCodec;
import net.ftgo.orderhistory.service.OrderHistoryQueryCriteria;
import net.ftgo.orderhistory.service.OrderHistoryQueryService;
import net.ftgo.orderhistory.service.OrderHistoryQueryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs",
    "ftgo.order-history.paging-secret=test-order-history-paging-secret-32-bytes",
    "ftgo.order-history.paging-token-ttl=PT15M"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    OrderHistoryController.class,
    OrderHistoryExceptionHandler.class,
    OrderHistoryQueryService.class,
    OrderHistoryPagingTokenCodec.class
})
class OrderHistoryPagingTokenMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderHistoryPagingTokenCodec codec;

    @MockBean
    private OrderHistoryRepository orderHistoryRepository;

    @MockBean
    private OrderHistoryQueryStore queryStore;

    @MockBean
    private OrderHistoryAuthorizationService authorizationService;

    @Test
    void tamperedTokenReturnsQueryInvalidBeforeStoreAccess() throws Exception {
        OrderHistoryQueryCriteria criteria = new OrderHistoryQueryCriteria(42L, null, null, null);
        String token = codec.encode(
            criteria,
            20,
            new OrderHistoryPageCursor(YearMonth.now(), null)
        );
        int mutationIndex = token.indexOf('.') - 1;
        char replacement = token.charAt(mutationIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, mutationIndex)
            + replacement
            + token.substring(mutationIndex + 1);

        mockMvc.perform(get("/api/consumers/42/orders")
                .param("pagingState", tampered)
                .with(authentication(consumerAuthentication(42L))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("ORDER_HISTORY_QUERY_INVALID"));

        verifyNoInteractions(queryStore);
    }

    @Test
    void crossQueryTokenReturnsQueryInvalidBeforeStoreAccess() throws Exception {
        String token = codec.encode(
            new OrderHistoryQueryCriteria(42L, "APPROVED", null, null),
            20,
            new OrderHistoryPageCursor(YearMonth.now(), null)
        );

        mockMvc.perform(get("/api/consumers/42/orders")
                .param("status", "REJECTED")
                .param("pagingState", token)
                .with(authentication(consumerAuthentication(42L))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("ORDER_HISTORY_QUERY_INVALID"));

        verifyNoInteractions(queryStore);
    }

    private AbstractAuthenticationToken consumerAuthentication(Long consumerId) {
        Instant now = Instant.now();
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
