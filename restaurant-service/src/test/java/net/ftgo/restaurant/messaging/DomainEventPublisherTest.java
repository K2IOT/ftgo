package net.ftgo.restaurant.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.observability.CorrelationContext;
import net.ftgo.restaurant.domain.RestaurantMenuChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DomainEventPublisher.
 */
@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    private static final String TRACEPARENT =
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Mock
    private OutboxRepository outboxRepository;

    private DomainEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new DomainEventPublisher(outboxRepository);
    }

    @Test
    void publishRestaurantEvent_shouldSaveToOutbox() {
        Long restaurantId = 1L;
        RestaurantMenuChanged event = menuChanged(restaurantId);
        when(outboxRepository.save(any(OutboxEntry.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        eventPublisher.publishRestaurantEvent(restaurantId, event);

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEntry savedEntry = captor.getValue();
        assertThat(savedEntry.getAggregateType()).isEqualTo("Restaurant");
        assertThat(savedEntry.getAggregateId()).isEqualTo("1");
        assertThat(savedEntry.getEventType()).isEqualTo("RestaurantMenuChanged");
        assertThat(savedEntry.getDestination())
            .isEqualTo("net.ftgo.restaurantservice.domain.Restaurant");
        assertThat(savedEntry.getPayload()).contains("Test Restaurant");
        assertThat(savedEntry.getPayload()).contains("Burger");
    }

    @Test
    void defaultPublisherCarriesCurrentCorrelationContextIntoOutboxEnvelope() throws Exception {
        Long restaurantId = 2L;
        when(outboxRepository.save(any(OutboxEntry.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        CorrelationContext.Snapshot snapshot = CorrelationContext.resolve(Map.of(
            CorrelationContext.TRACEPARENT, TRACEPARENT,
            CorrelationContext.TRACESTATE, "vendor=value",
            CorrelationContext.BAGGAGE, "tenant=t-202",
            CorrelationContext.CORRELATION_ID, "corr-outbox-202",
            CorrelationContext.CAUSATION_ID, "command-202"
        ));

        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            eventPublisher.publishRestaurantEvent(restaurantId, menuChanged(restaurantId));
        }

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        JsonNode envelope = new ObjectMapper().readTree(captor.getValue().getPayload());

        assertThat(envelope.at("/metadata/correlationId").asText())
            .isEqualTo("corr-outbox-202");
        assertThat(envelope.at("/metadata/causationId").asText())
            .isEqualTo("command-202");
        assertThat(envelope.at("/metadata/trace/traceparent").asText())
            .isEqualTo(TRACEPARENT);
        assertThat(envelope.at("/metadata/trace/tracestate").asText())
            .isEqualTo("vendor=value");
        assertThat(envelope.at("/metadata/trace/baggage").asText())
            .contains("tenant=t-202")
            .contains("ftgo.correlation_id=corr-outbox-202");
    }

    private RestaurantMenuChanged menuChanged(Long restaurantId) {
        RestaurantMenuChanged.MenuItemInfo menuItem = new RestaurantMenuChanged.MenuItemInfo(
            1L, "Burger", "Delicious burger", "12.99", true
        );
        return new RestaurantMenuChanged(
            restaurantId,
            "Test Restaurant",
            Arrays.asList(menuItem)
        );
    }
}
