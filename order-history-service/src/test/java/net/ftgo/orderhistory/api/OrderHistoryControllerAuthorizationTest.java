package net.ftgo.orderhistory.api;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.orderhistory.config.SecurityConfiguration;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.security.OrderHistoryAuthorizationService;
import net.ftgo.orderhistory.service.OrderHistoryQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    OrderHistoryController.class
})
class OrderHistoryControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderHistoryRepository orderHistoryRepository;

    @MockBean
    private OrderHistoryQueryService queryService;

    @MockBean
    private OrderHistoryAuthorizationService authorizationService;

    @Test
    void rejectsCrossConsumerHistoryBeforeQueryingStore() throws Exception {
        doThrow(new AccessDeniedException("Order history access denied"))
            .when(authorizationService)
            .requireConsumerAccess(eq(202L), any());

        mockMvc.perform(get("/api/consumers/202/orders")
                .with(authentication(consumerAuthentication(101L))))
            .andExpect(status().isForbidden());

        verify(queryService, never()).query(any(), any(Integer.class), any());
    }

    @Test
    void rejectsCrossConsumerDirectOrderWithoutSerializingRecord() throws Exception {
        OrderHistoryRecord record = order("9001", 202L);
        record.setDeliveryAddress("secret tenant address");
        when(orderHistoryRepository.findById("9001")).thenReturn(Optional.of(record));
        doThrow(new AccessDeniedException("Order history access denied"))
            .when(authorizationService)
            .requireOrderAccess(eq(record), any());

        mockMvc.perform(get("/api/orders/9001")
                .with(authentication(consumerAuthentication(101L))))
            .andExpect(status().isForbidden())
            .andExpect(content().string(not(containsString("secret tenant address"))));
    }

    @Test
    void permitsOwnerDirectOrderReadAfterAuthorization() throws Exception {
        OrderHistoryRecord record = order("9001", 101L);
        when(orderHistoryRepository.findById("9001")).thenReturn(Optional.of(record));

        mockMvc.perform(get("/api/orders/9001")
                .with(authentication(consumerAuthentication(101L))))
            .andExpect(status().isOk());

        verify(authorizationService).requireOrderAccess(eq(record), any());
    }

    @Test
    void returnsNotFoundWithoutInvokingOwnershipCheck() throws Exception {
        when(orderHistoryRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/missing")
                .with(authentication(consumerAuthentication(101L))))
            .andExpect(status().isNotFound());

        verify(authorizationService, never()).requireOrderAccess(any(), any());
    }

    private OrderHistoryRecord order(String orderId, Long consumerId) {
        OrderHistoryRecord record = new OrderHistoryRecord(orderId);
        record.setConsumerId(consumerId);
        return record;
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
