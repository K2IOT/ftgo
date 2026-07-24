package net.ftgo.restaurant.service;

import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.common.orderflow.menu.OrderMenuLineItem;
import net.ftgo.common.orderflow.replies.OrderMenuValidated;
import net.ftgo.common.orderflow.replies.OrderMenuValidationRejected;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.repository.MenuItemRepository;
import net.ftgo.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMenuValidationServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    private OrderMenuValidationService service;

    @BeforeEach
    void setUp() {
        service = new OrderMenuValidationService(restaurantRepository, menuItemRepository);
    }

    @Test
    void exactAuthoritativeSnapshotReturnsCurrentMenuAndTotal() {
        Restaurant restaurant = restaurant();
        MenuItem burger = menuItem(11L, "Burger", "12.50", true);
        MenuItem fries = menuItem(12L, "Fries", "4.00", true);
        when(restaurantRepository.findById(202L)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantIdAndIdIn(202L, List.of(11L, 12L)))
            .thenReturn(List.of(fries, burger));

        OrderMenuValidated result = service.validate(command(
            0L,
            List.of(
                new OrderMenuLineItem(11L, "Burger", new Money("12.50"), 2),
                new OrderMenuLineItem(12L, "Fries", new Money("4.00"), 1)
            )
        ));

        assertThat(result.getOrderId()).isEqualTo(101L);
        assertThat(result.getRestaurantId()).isEqualTo(202L);
        assertThat(result.getCurrentMenuVersion()).isZero();
        assertThat(result.getAuthoritativeLineItems())
            .extracting(OrderMenuLineItem::getMenuItemId)
            .containsExactly(11L, 12L);
        assertThat(result.getAuthoritativeTotal()).isEqualTo(new Money("29.00"));
    }

    @Test
    void missingRestaurantUsesStableReasonCode() {
        when(restaurantRepository.findById(202L)).thenReturn(Optional.empty());

        assertReason(command(0L, List.of(item(11L, "12.50"))),
            OrderMenuValidationRejected.RESTAURANT_NOT_FOUND);
    }

    @Test
    void closedRestaurantUsesStableReasonCode() {
        Restaurant restaurant = restaurant();
        restaurant.closeForOrders();
        when(restaurantRepository.findById(202L)).thenReturn(Optional.of(restaurant));

        assertReason(command(0L, List.of(item(11L, "12.50"))),
            OrderMenuValidationRejected.RESTAURANT_CLOSED);
    }

    @Test
    void staleMenuVersionIsRejectedBeforeItemLookup() {
        Restaurant restaurant = restaurant();
        restaurant.incrementMenuVersion();
        when(restaurantRepository.findById(202L)).thenReturn(Optional.of(restaurant));

        assertReason(command(0L, List.of(item(11L, "12.50"))),
            OrderMenuValidationRejected.MENU_VERSION_CHANGED);
    }

    @Test
    void duplicateOrNonPositiveQuantityIsRejected() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findById(202L)).thenReturn(Optional.of(restaurant));

        assertReason(command(0L, List.of(item(11L, "12.50"), item(11L, "12.50"))),
            OrderMenuValidationRejected.INVALID_QUANTITY);
        assertReason(command(0L, List.of(new OrderMenuLineItem(11L, "Burger", new Money("12.50"), 0))),
            OrderMenuValidationRejected.INVALID_QUANTITY);
    }

    @Test
    void missingUnavailableAndChangedPriceHaveDistinctCodes() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findById(202L)).thenReturn(Optional.of(restaurant));

        when(menuItemRepository.findByRestaurantIdAndIdIn(202L, List.of(11L))).thenReturn(List.of());
        assertReason(command(0L, List.of(item(11L, "12.50"))),
            OrderMenuValidationRejected.MENU_ITEM_NOT_FOUND);

        when(menuItemRepository.findByRestaurantIdAndIdIn(202L, List.of(11L)))
            .thenReturn(List.of(menuItem(11L, "Burger", "12.50", false)));
        assertReason(command(0L, List.of(item(11L, "12.50"))),
            OrderMenuValidationRejected.MENU_ITEM_UNAVAILABLE);

        when(menuItemRepository.findByRestaurantIdAndIdIn(202L, List.of(11L)))
            .thenReturn(List.of(menuItem(11L, "Burger", "13.00", true)));
        assertReason(command(0L, List.of(item(11L, "12.50"))),
            OrderMenuValidationRejected.MENU_PRICE_CHANGED);
    }

    private void assertReason(ValidateOrderMenuCommand command, String reasonCode) {
        assertThatThrownBy(() -> service.validate(command))
            .isInstanceOf(OrderMenuValidationException.class)
            .extracting("reasonCode")
            .isEqualTo(reasonCode);
    }

    private ValidateOrderMenuCommand command(Long menuVersion, List<OrderMenuLineItem> items) {
        return new ValidateOrderMenuCommand(101L, 202L, menuVersion, items);
    }

    private OrderMenuLineItem item(Long id, String price) {
        return new OrderMenuLineItem(id, "Burger", new Money(price), 1);
    }

    private Restaurant restaurant() {
        return new Restaurant(
            "Test Restaurant",
            new Address("1 Main St", "Hanoi", "HN", "10000"),
            "{}"
        );
    }

    private MenuItem menuItem(Long id, String name, String price, boolean available) {
        MenuItem item = new MenuItem(202L, name, "", new Money(price));
        item.setAvailable(available);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
