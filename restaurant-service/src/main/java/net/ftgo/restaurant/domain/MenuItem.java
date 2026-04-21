package net.ftgo.restaurant.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Money;

import java.time.LocalDateTime;

/**
 * MenuItem entity representing a menu item in a restaurant.
 * 
 * Each menu item belongs to a restaurant and has a name, description, price, and availability status.
 * Invariant: price must be a positive decimal value.
 */
@Entity
@Table(name = "menu_items", indexes = {
    @Index(name = "idx_restaurant_id", columnList = "restaurant_id"),
    @Index(name = "idx_available", columnList = "available")
})
public class MenuItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Restaurant ID is required")
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;
    
    @NotBlank(message = "Menu item name is required")
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @NotNull(message = "Price is required")
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false, precision = 10, scale = 2))
    })
    private Money price;
    
    @NotNull
    @Column(nullable = false)
    private Boolean available = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Default constructor for JPA.
     */
    protected MenuItem() {
    }
    
    /**
     * Creates a new MenuItem with the specified details.
     * 
     * @param restaurantId the ID of the restaurant this menu item belongs to
     * @param name the menu item's name
     * @param description the menu item's description
     * @param price the menu item's price (must be positive)
     * @throws IllegalArgumentException if price is not positive
     */
    public MenuItem(Long restaurantId, String name, String description, Money price) {
        validateRestaurantId(restaurantId);
        validateName(name);
        validatePrice(price);
        
        this.restaurantId = restaurantId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.available = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Validates that the restaurant ID is not null.
     * 
     * @param restaurantId the restaurant ID to validate
     * @throws IllegalArgumentException if restaurantId is null
     */
    private void validateRestaurantId(Long restaurantId) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("Restaurant ID cannot be null");
        }
    }
    
    /**
     * Validates that the menu item name is not null or blank.
     * 
     * @param name the name to validate
     * @throws IllegalArgumentException if name is null or blank
     */
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Menu item name cannot be null or blank");
        }
    }
    
    /**
     * Validates that the price is a positive decimal value.
     * 
     * @param price the price to validate
     * @throws IllegalArgumentException if price is null or not positive
     */
    private void validatePrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }
        if (price.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Price must be a positive decimal value");
        }
    }
    
    /**
     * Updates the menu item's price.
     * 
     * @param newPrice the new price (must be positive)
     * @throws IllegalArgumentException if newPrice is not positive
     */
    public void updatePrice(Money newPrice) {
        validatePrice(newPrice);
        this.price = newPrice;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Updates the menu item's details.
     * 
     * @param name the new name (optional)
     * @param description the new description (optional)
     * @param price the new price (optional, must be positive if provided)
     */
    public void updateDetails(String name, String description, Money price) {
        if (name != null && !name.isBlank()) {
            validateName(name);
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            validatePrice(price);
            this.price = price;
        }
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Sets the availability status of the menu item.
     * 
     * @param available true if the item is available, false otherwise
     */
    public void setAvailable(boolean available) {
        this.available = available;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Checks if the menu item is currently available.
     * 
     * @return true if the item is available, false otherwise
     */
    public boolean isAvailable() {
        return available;
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Money getPrice() {
        return price;
    }
    
    public Boolean getAvailable() {
        return available;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
