package net.ftgo.restaurant.service;

import net.ftgo.common.Money;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.common.orderflow.menu.OrderMenuLineItem;
import net.ftgo.common.orderflow.replies.OrderMenuValidated;
import net.ftgo.common.orderflow.replies.OrderMenuValidationRejected;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.repository.MenuItemRepository;
import net.ftgo.restaurant.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class OrderMenuValidationService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;

    public OrderMenuValidationService(RestaurantRepository restaurantRepository,
                                      MenuItemRepository menuItemRepository) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
    }

    /**
     * Expected menu rejections are converted by the command handler into typed
     * saga failure replies. They must not mark Eventuate's surrounding message
     * transaction rollback-only, otherwise the rejection reply is lost and the
     * consumer terminates with an UnexpectedRollbackException.
     */
    @Transactional(readOnly = true, noRollbackFor = OrderMenuValidationException.class)
    public OrderMenuValidated validate(ValidateOrderMenuCommand command) {
        Restaurant restaurant = restaurantRepository.findById(command.getRestaurantId())
            .orElseThrow(() -> rejected(
                OrderMenuValidationRejected.RESTAURANT_NOT_FOUND,
                "Restaurant not found: " + command.getRestaurantId()
            ));

        if (!restaurant.isEnabled() || !restaurant.isAcceptingOrders()) {
            throw rejected(OrderMenuValidationRejected.RESTAURANT_CLOSED,
                "Restaurant is not accepting orders");
        }

        if (!Objects.equals(restaurant.getMenuVersion(), command.getExpectedMenuVersion())) {
            throw rejected(OrderMenuValidationRejected.MENU_VERSION_CHANGED,
                "Menu version changed from " + command.getExpectedMenuVersion()
                    + " to " + restaurant.getMenuVersion());
        }

        List<OrderMenuLineItem> requested = command.getLineItems();
        if (requested == null || requested.isEmpty()) {
            throw rejected(OrderMenuValidationRejected.INVALID_QUANTITY,
                "At least one line item is required");
        }

        List<Long> ids = new ArrayList<>(requested.size());
        Set<Long> uniqueIds = new HashSet<>();
        for (OrderMenuLineItem lineItem : requested) {
            if (lineItem == null || lineItem.getMenuItemId() == null
                || lineItem.getQuantity() == null || lineItem.getQuantity() <= 0
                || !uniqueIds.add(lineItem.getMenuItemId())) {
                throw rejected(OrderMenuValidationRejected.INVALID_QUANTITY,
                    "Line items require a unique menu item ID and positive quantity");
            }
            ids.add(lineItem.getMenuItemId());
        }

        Map<Long, MenuItem> authoritativeById = new HashMap<>();
        for (MenuItem menuItem : menuItemRepository.findByRestaurantIdAndIdIn(
            command.getRestaurantId(), ids)) {
            authoritativeById.put(menuItem.getId(), menuItem);
        }

        List<OrderMenuLineItem> authoritativeItems = new ArrayList<>(requested.size());
        Money total = Money.ZERO;
        for (OrderMenuLineItem requestedItem : requested) {
            MenuItem current = authoritativeById.get(requestedItem.getMenuItemId());
            if (current == null) {
                throw rejected(OrderMenuValidationRejected.MENU_ITEM_NOT_FOUND,
                    "Menu item not found: " + requestedItem.getMenuItemId());
            }
            if (!current.isAvailable()) {
                throw rejected(OrderMenuValidationRejected.MENU_ITEM_UNAVAILABLE,
                    "Menu item unavailable: " + requestedItem.getMenuItemId());
            }
            if (!current.getPrice().equals(requestedItem.getExpectedUnitPrice())) {
                throw rejected(OrderMenuValidationRejected.MENU_PRICE_CHANGED,
                    "Menu item price changed: " + requestedItem.getMenuItemId());
            }
            if (!Objects.equals(current.getName(), requestedItem.getExpectedName())) {
                throw rejected(OrderMenuValidationRejected.MENU_VERSION_CHANGED,
                    "Menu item details changed: " + requestedItem.getMenuItemId());
            }

            OrderMenuLineItem authoritative = new OrderMenuLineItem(
                current.getId(), current.getName(), current.getPrice(), requestedItem.getQuantity());
            authoritativeItems.add(authoritative);
            total = total.add(current.getPrice().multiply(requestedItem.getQuantity()));
        }

        return new OrderMenuValidated(
            command.getOrderId(),
            restaurant.getId() != null ? restaurant.getId() : command.getRestaurantId(),
            restaurant.getMenuVersion(),
            restaurant.getAddress(),
            authoritativeItems,
            total
        );
    }

    private OrderMenuValidationException rejected(String reasonCode, String message) {
        return new OrderMenuValidationException(reasonCode, message);
    }
}
