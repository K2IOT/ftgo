package net.ftgo.restaurant.api;

import net.ftgo.common.Money;
import net.ftgo.restaurant.domain.MenuItem;

import java.time.LocalDateTime;

/**
 * Response DTO for menu item information.
 */
public class MenuItemResponse {
    
    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private Money price;
    private Boolean available;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public MenuItemResponse() {
    }
    
    public MenuItemResponse(MenuItem menuItem) {
        this.id = menuItem.getId();
        this.restaurantId = menuItem.getRestaurantId();
        this.name = menuItem.getName();
        this.description = menuItem.getDescription();
        this.price = menuItem.getPrice();
        this.available = menuItem.getAvailable();
        this.createdAt = menuItem.getCreatedAt();
        this.updatedAt = menuItem.getUpdatedAt();
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
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
    
    public Money getPrice() {
        return price;
    }
    
    public void setPrice(Money price) {
        this.price = price;
    }
    
    public Boolean getAvailable() {
        return available;
    }
    
    public void setAvailable(Boolean available) {
        this.available = available;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
