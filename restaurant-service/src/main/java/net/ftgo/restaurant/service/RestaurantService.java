package net.ftgo.restaurant.service;

import net.ftgo.common.Money;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.domain.RestaurantMenuChanged;
import net.ftgo.restaurant.messaging.DomainEventPublisher;
import net.ftgo.restaurant.repository.MenuItemRepository;
import net.ftgo.restaurant.repository.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RestaurantService {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantService.class);

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final DomainEventPublisher eventPublisher;

    public RestaurantService(RestaurantRepository restaurantRepository,
                             MenuItemRepository menuItemRepository,
                             DomainEventPublisher eventPublisher) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Restaurant createRestaurant(Restaurant restaurant) {
        return restaurantRepository.save(restaurant);
    }

    public Restaurant findRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }

    @Transactional
    public MenuItem createMenuItem(Long restaurantId, MenuItem menuItem) {
        Restaurant restaurant = findRestaurant(restaurantId);
        MenuItem saved = menuItemRepository.save(menuItem);
        advanceMenuVersion(restaurant);
        publishMenuChangedEvent(restaurantId, restaurant);
        return saved;
    }

    @Transactional
    public MenuItem updateMenuItem(Long restaurantId, Long menuItemId,
                                   String name, String description,
                                   Money price, Boolean available) {
        Restaurant restaurant = findRestaurant(restaurantId);
        MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId)
            .orElseThrow(() -> new MenuItemNotFoundException(restaurantId, menuItemId));

        if (name != null || description != null || price != null) {
            menuItem.updateDetails(name, description, price);
        }
        if (available != null) {
            menuItem.setAvailable(available);
        }

        MenuItem updated = menuItemRepository.save(menuItem);
        advanceMenuVersion(restaurant);
        publishMenuChangedEvent(restaurantId, restaurant);
        return updated;
    }

    @Transactional
    public void deleteMenuItem(Long restaurantId, Long menuItemId) {
        Restaurant restaurant = findRestaurant(restaurantId);
        MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId)
            .orElseThrow(() -> new MenuItemNotFoundException(restaurantId, menuItemId));
        menuItemRepository.delete(menuItem);
        advanceMenuVersion(restaurant);
        publishMenuChangedEvent(restaurantId, restaurant);
    }

    public List<MenuItem> getMenuItems(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId);
    }

    /**
     * Preserves the original service API while the saga path uses
     * {@link OrderMenuValidationService} for authoritative batch validation.
     */
    public boolean validateMenuItems(Long restaurantId, List<Long> menuItemIds) {
        for (Long menuItemId : menuItemIds) {
            MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId)
                .orElse(null);
            if (menuItem == null || !menuItem.isAvailable()) {
                return false;
            }
        }
        return true;
    }

    private void advanceMenuVersion(Restaurant restaurant) {
        restaurant.incrementMenuVersion();
        restaurantRepository.save(restaurant);
    }

    private void publishMenuChangedEvent(Long restaurantId, Restaurant restaurant) {
        List<RestaurantMenuChanged.MenuItemInfo> menuItemInfos = menuItemRepository
            .findByRestaurantId(restaurantId)
            .stream()
            .map(item -> new RestaurantMenuChanged.MenuItemInfo(
                item.getId(), item.getName(), item.getDescription(),
                item.getPrice().toString(), item.getAvailable()))
            .collect(Collectors.toList());

        eventPublisher.publishRestaurantEvent(restaurantId, new RestaurantMenuChanged(
            restaurantId, restaurant.getName(), menuItemInfos));
        logger.info("Published menu version {} for restaurant {}", restaurant.getMenuVersion(), restaurantId);
    }
}
