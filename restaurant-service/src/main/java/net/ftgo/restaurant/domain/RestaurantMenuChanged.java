package net.ftgo.restaurant.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain event published when a restaurant's menu is changed.
 * This includes menu item additions, updates, deletions, and availability changes.
 */
public class RestaurantMenuChanged {
    
    private Long restaurantId;
    private String restaurantName;
    private List<MenuItemInfo> menuItems;
    private LocalDateTime changedAt;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public RestaurantMenuChanged() {
    }
    
    public RestaurantMenuChanged(Long restaurantId, String restaurantName, 
                                List<MenuItemInfo> menuItems) {
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.menuItems = menuItems;
        this.changedAt = LocalDateTime.now();
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }
    
    public String getRestaurantName() {
        return restaurantName;
    }
    
    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }
    
    public List<MenuItemInfo> getMenuItems() {
        return menuItems;
    }
    
    public void setMenuItems(List<MenuItemInfo> menuItems) {
        this.menuItems = menuItems;
    }
    
    public LocalDateTime getChangedAt() {
        return changedAt;
    }
    
    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
    
    /**
     * Information about a menu item included in the event.
     */
    public static class MenuItemInfo {
        private Long id;
        private String name;
        private String description;
        private String price;
        private Boolean available;
        
        public MenuItemInfo() {
        }
        
        public MenuItemInfo(Long id, String name, String description, String price, Boolean available) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.price = price;
            this.available = available;
        }
        
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getPrice() {
            return price;
        }
        
        public void setPrice(String price) {
            this.price = price;
        }
        
        public Boolean getAvailable() {
            return available;
        }
        
        public void setAvailable(Boolean available) {
            this.available = available;
        }
    }
}
