package net.ftgo.restaurant.service;

import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.domain.RestaurantMenuChanged;
import net.ftgo.restaurant.messaging.DomainEventPublisher;
import net.ftgo.restaurant.repository.MenuItemRepository;
import net.ftgo.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RestaurantService.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {
    
    @Mock
    private RestaurantRepository restaurantRepository;
    
    @Mock
    private MenuItemRepository menuItemRepository;
    
    @Mock
    private DomainEventPublisher eventPublisher;
    
    private RestaurantService restaurantService;
    
    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService(restaurantRepository, menuItemRepository, eventPublisher);
    }
    
    @Test
    void createRestaurant_shouldSaveAndReturnRestaurant() {
        // Given
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant("Test Restaurant", address, "{\"monday\": \"9:00-22:00\"}");
        
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(restaurant);
        
        // When
        Restaurant result = restaurantService.createRestaurant(restaurant);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Restaurant");
        verify(restaurantRepository).save(restaurant);
    }
    
    @Test
    void findRestaurant_shouldReturnRestaurant() {
        // Given
        Long restaurantId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant("Test Restaurant", address, "{\"monday\": \"9:00-22:00\"}");
        
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        
        // When
        Restaurant result = restaurantService.findRestaurant(restaurantId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Restaurant");
        verify(restaurantRepository).findById(restaurantId);
    }
    
    @Test
    void findRestaurant_whenNotFound_shouldThrowException() {
        // Given
        Long restaurantId = 999L;
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> restaurantService.findRestaurant(restaurantId))
            .isInstanceOf(RestaurantNotFoundException.class)
            .hasMessageContaining("Restaurant not found: 999");
        
        verify(restaurantRepository).findById(restaurantId);
    }
    
    @Test
    void createMenuItem_shouldSaveMenuItemAndPublishEvent() {
        // Given
        Long restaurantId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant("Test Restaurant", address, "{\"monday\": \"9:00-22:00\"}");
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(menuItem);
        when(menuItemRepository.findByRestaurantId(restaurantId)).thenReturn(Arrays.asList(menuItem));
        
        // When
        MenuItem result = restaurantService.createMenuItem(restaurantId, menuItem);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Burger");
        verify(menuItemRepository).save(menuItem);
        
        // Verify event was published
        ArgumentCaptor<RestaurantMenuChanged> eventCaptor = ArgumentCaptor.forClass(RestaurantMenuChanged.class);
        verify(eventPublisher).publishRestaurantEvent(eq(restaurantId), eventCaptor.capture());
        
        RestaurantMenuChanged event = eventCaptor.getValue();
        assertThat(event.getRestaurantId()).isEqualTo(restaurantId);
        assertThat(event.getRestaurantName()).isEqualTo("Test Restaurant");
        assertThat(event.getMenuItems()).hasSize(1);
        assertThat(event.getMenuItems().get(0).getName()).isEqualTo("Burger");
    }
    
    @Test
    void createMenuItem_whenRestaurantNotFound_shouldThrowException() {
        // Given
        Long restaurantId = 999L;
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> restaurantService.createMenuItem(restaurantId, menuItem))
            .isInstanceOf(RestaurantNotFoundException.class);
        
        verify(menuItemRepository, never()).save(any());
        verify(eventPublisher, never()).publishRestaurantEvent(any(), any());
    }
    
    @Test
    void updateMenuItem_shouldUpdateAndPublishEvent() {
        // Given
        Long restaurantId = 1L;
        Long menuItemId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant("Test Restaurant", address, "{\"monday\": \"9:00-22:00\"}");
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId))
            .thenReturn(Optional.of(menuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(menuItem);
        when(menuItemRepository.findByRestaurantId(restaurantId)).thenReturn(Arrays.asList(menuItem));
        
        // When
        MenuItem result = restaurantService.updateMenuItem(
            restaurantId, menuItemId, "Updated Burger", null, new Money("13.99"), false);
        
        // Then
        assertThat(result).isNotNull();
        verify(menuItemRepository).save(menuItem);
        verify(eventPublisher).publishRestaurantEvent(eq(restaurantId), any(RestaurantMenuChanged.class));
    }
    
    @Test
    void deleteMenuItem_shouldDeleteAndPublishEvent() {
        // Given
        Long restaurantId = 1L;
        Long menuItemId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant("Test Restaurant", address, "{\"monday\": \"9:00-22:00\"}");
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId))
            .thenReturn(Optional.of(menuItem));
        when(menuItemRepository.findByRestaurantId(restaurantId)).thenReturn(Arrays.asList());
        
        // When
        restaurantService.deleteMenuItem(restaurantId, menuItemId);
        
        // Then
        verify(menuItemRepository).delete(menuItem);
        verify(eventPublisher).publishRestaurantEvent(eq(restaurantId), any(RestaurantMenuChanged.class));
    }
    
    @Test
    void validateMenuItems_whenAllItemsExistAndAvailable_shouldReturnTrue() {
        // Given
        Long restaurantId = 1L;
        MenuItem item1 = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        MenuItem item2 = new MenuItem(restaurantId, "Fries", "Crispy fries", new Money("4.99"));
        
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, 1L))
            .thenReturn(Optional.of(item1));
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, 2L))
            .thenReturn(Optional.of(item2));
        
        // When
        boolean result = restaurantService.validateMenuItems(restaurantId, Arrays.asList(1L, 2L));
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void validateMenuItems_whenItemNotFound_shouldReturnFalse() {
        // Given
        Long restaurantId = 1L;
        
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, 999L))
            .thenReturn(Optional.empty());
        
        // When
        boolean result = restaurantService.validateMenuItems(restaurantId, Arrays.asList(999L));
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void validateMenuItems_whenItemNotAvailable_shouldReturnFalse() {
        // Given
        Long restaurantId = 1L;
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        menuItem.setAvailable(false);
        
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, 1L))
            .thenReturn(Optional.of(menuItem));
        
        // When
        boolean result = restaurantService.validateMenuItems(restaurantId, Arrays.asList(1L));
        
        // Then
        assertThat(result).isFalse();
    }
}
