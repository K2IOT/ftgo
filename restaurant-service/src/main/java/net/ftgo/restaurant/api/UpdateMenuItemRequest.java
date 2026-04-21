package net.ftgo.restaurant.api;

import net.ftgo.common.Money;

/**
 * Request DTO for updating a menu item.
 * All fields are optional - only provided fields will be updated.
 */
public class UpdateMenuItemRequest {
    
    private String name;
    private String description;
    private Money price;
    private Boolean available;
    
    public UpdateMenuItemRequest() {
    }
    
    public UpdateMenuItemRequest(String name, String description, Money price, Boolean available) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.available = available;
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
}
