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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        restaurantService = new RestaurantService(
            restaurantRepository,
            menuItemRepository,
            eventPublisher
        );
    }

    @Test
    void createRestaurant_shouldSaveAndReturnRestaurant() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant(
            "Test Restaurant",
            address,
            "{\"monday\": \"9:00-22:00\"}"
        );
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(restaurant);

        Restaurant result = restaurantService.createRestaurant(restaurant);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Restaurant");
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    void findRestaurant_shouldReturnRestaurant() {
        Long restaurantId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant(
            "Test Restaurant",
            address,
            "{\"monday\": \"9:00-22:00\"}"
        );
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        Restaurant result = restaurantService.findRestaurant(restaurantId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Restaurant");
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void findRestaurant_whenNotFound_shouldThrowException() {
        Long restaurantId = 999L;
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.findRestaurant(restaurantId))
            .isInstanceOf(RestaurantNotFoundException.class)
            .hasMessageContaining("Restaurant not found: 999");

        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void createMenuItem_shouldSaveMenuItemAndPublishVersionedEvent() {
        Long restaurantId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant(
            "Test Restaurant",
            address,
            "{\"monday\": \"9:00-22:00\"}"
        );
        MenuItem menuItem = new MenuItem(
            restaurantId,
            "Burger",
            "Delicious burger",
            new Money("12.99")
        );
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(menuItem);
        when(menuItemRepository.findByRestaurantId(restaurantId)).thenReturn(Arrays.asList(menuItem));

        MenuItem result = restaurantService.createMenuItem(restaurantId, menuItem);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Burger");
        verify(menuItemRepository).save(menuItem);
        verify(restaurantRepository).saveAndFlush(restaurant);

        ArgumentCaptor<RestaurantMenuChanged> eventCaptor =
            ArgumentCaptor.forClass(RestaurantMenuChanged.class);
        verify(eventPublisher).publishRestaurantEvent(
            eq(restaurantId),
            eq(restaurant.getVersion()),
            eventCaptor.capture()
        );

        RestaurantMenuChanged event = eventCaptor.getValue();
        assertThat(event.getRestaurantId()).isEqualTo(restaurantId);
        assertThat(event.getRestaurantName()).isEqualTo("Test Restaurant");
        assertThat(event.getMenuItems()).hasSize(1);
        assertThat(event.getMenuItems().get(0).getName()).isEqualTo("Burger");
    }

    @Test
    void createMenuItem_whenRestaurantNotFound_shouldThrowException() {
        Long restaurantId = 999L;
        MenuItem menuItem = new MenuItem(
            restaurantId,
            "Burger",
            "Delicious burger",
            new Money("12.99")
        );
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.createMenuItem(restaurantId, menuItem))
            .isInstanceOf(RestaurantNotFoundException.class);

        verify(menuItemRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateMenuItem_shouldUpdateAndPublishVersionedEvent() {
        Long restaurantId = 1L;
        Long menuItemId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant(
            "Test Restaurant",
            address,
            "{\"monday\": \"9:00-22:00\"}"
        );
        MenuItem menuItem = new MenuItem(
            restaurantId,
            "Burger",
            "Delicious burger",
            new Money("12.99")
        );
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId))
            .thenReturn(Optional.of(menuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(menuItem);
        when(menuItemRepository.findByRestaurantId(restaurantId)).thenReturn(Arrays.asList(menuItem));

        MenuItem result = restaurantService.updateMenuItem(
            restaurantId,
            menuItemId,
            "Updated Burger",
            null,
            new Money("13.99"),
            false
        );

        assertThat(result).isNotNull();
        verify(menuItemRepository).save(menuItem);
        verify(restaurantRepository).saveAndFlush(restaurant);
        verify(eventPublisher).publishRestaurantEvent(
            eq(restaurantId),
            eq(restaurant.getVersion()),
            any(RestaurantMenuChanged.class)
        );
    }

    @Test
    void deleteMenuItem_shouldDeleteAndPublishVersionedEvent() {
        Long restaurantId = 1L;
        Long menuItemId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant(
            "Test Restaurant",
            address,
            "{\"monday\": \"9:00-22:00\"}"
        );
        MenuItem menuItem = new MenuItem(
            restaurantId,
            "Burger",
            "Delicious burger",
            new Money("12.99")
        );
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId))
            .thenReturn(Optional.of(menuItem));
        when(menuItemRepository.findByRestaurantId(restaurantId)).thenReturn(Arrays.asList());

        restaurantService.deleteMenuItem(restaurantId, menuItemId);

        verify(menuItemRepository).delete(menuItem);
        verify(restaurantRepository).saveAndFlush(restaurant);
        verify(eventPublisher).publishRestaurantEvent(
            eq(restaurantId),
            eq(restaurant.getVersion()),
            any(RestaurantMenuChanged.class)
        );
    }

    @Test
    void validateMenuItems_whenAllItemsExistAndAvailable_shouldReturnTrue() {
        Long restaurantId = 1L;
        MenuItem item1 = new MenuItem(
            restaurantId,
            "Burger",
            "Delicious burger",
            new Money("12.99")
        );
        MenuItem item2 = new MenuItem(
            restaurantId,
            "Fries",
            "Crispy fries",
            new Money("4.99")
        );
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, 1L))
            .thenReturn(Optional.of(item1));
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, 2L))
            .thenReturn(Optional.of(item2));

        boolean result = restaurantService.validateMenuItems(
            restaurantId,
            Arrays.asList(1L, 2L)
        );

        assertThat(result).isTrue();
    }

    @Test
    void validateMenuItems_whenItemNotFound_shouldReturnFalse() {
        Long restaurantId = 1L;
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, 999L))
            .thenReturn(Optional.empty());

        boolean result = restaurantService.validateMenuItems(
            restaurantId,
            Arrays.asList(999L)
        );

        assertThat(result).isFalse();
    }

    @Test
    void validateMenuItems_whenItemNotAvailable_shouldReturnFalse() {
        Long restaurantId = 1L;
        MenuItem menuItem = new MenuItem(
            restaurantId,
            "Burger",
            "Delicious burger",
            new Money("12.99")
        );
        menuItem.setAvailable(false);
        when(menuItemRepository.findByRestaurantIdAndId(restaurantId, 1L))
            .thenReturn(Optional.of(menuItem));

        boolean result = restaurantService.validateMenuItems(
            restaurantId,
            Arrays.asList(1L)
        );

        assertThat(result).isFalse();
    }
}
