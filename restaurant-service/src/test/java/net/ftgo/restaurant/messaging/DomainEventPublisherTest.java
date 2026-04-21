package net.ftgo.restaurant.messaging;

import net.ftgo.restaurant.domain.RestaurantMenuChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DomainEventPublisher.
 */
@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {
    
    @Mock
    private OutboxRepository outboxRepository;
    
    private DomainEventPublisher eventPublisher;
    
    @BeforeEach
    void setUp() {
        eventPublisher = new DomainEventPublisher(outboxRepository);
    }
    
    @Test
    void publishRestaurantEvent_shouldSaveToOutbox() {
        // Given
        Long restaurantId = 1L;
        RestaurantMenuChanged.MenuItemInfo menuItem = new RestaurantMenuChanged.MenuItemInfo(
            1L, "Burger", "Delicious burger", "12.99", true
        );
        RestaurantMenuChanged event = new RestaurantMenuChanged(
            restaurantId,
            "Test Restaurant",
            Arrays.asList(menuItem)
        );
        
        when(outboxRepository.save(any(OutboxEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        eventPublisher.publishRestaurantEvent(restaurantId, event);
        
        // Then
        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        
        OutboxEntry savedEntry = captor.getValue();
        assertThat(savedEntry.getAggregateType()).isEqualTo("Restaurant");
        assertThat(savedEntry.getAggregateId()).isEqualTo("1");
        assertThat(savedEntry.getEventType()).isEqualTo("RestaurantMenuChanged");
        assertThat(savedEntry.getDestination()).isEqualTo("net.ftgo.restaurantservice.domain.Restaurant");
        assertThat(savedEntry.getPayload()).contains("Test Restaurant");
        assertThat(savedEntry.getPayload()).contains("Burger");
    }
}
