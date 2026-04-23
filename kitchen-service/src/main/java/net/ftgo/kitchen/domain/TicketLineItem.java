package net.ftgo.kitchen.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Entity representing a line item in a kitchen ticket.
 * 
 * Each line item corresponds to a menu item from the order with quantity.
 */
@Entity
@Table(name = "ticket_line_items")
public class TicketLineItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Menu item ID is required")
    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;
    
    @NotBlank(message = "Name is required")
    @Column(name = "name", nullable = false)
    private String name;
    
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    /**
     * Default constructor for JPA.
     */
    protected TicketLineItem() {
    }
    
    /**
     * Creates a new TicketLineItem.
     * 
     * @param menuItemId the menu item ID
     * @param name the menu item name
     * @param quantity the quantity ordered
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public TicketLineItem(Long menuItemId, String name, Integer quantity) {
        validateMenuItemId(menuItemId);
        validateName(name);
        validateQuantity(quantity);
        
        this.menuItemId = menuItemId;
        this.name = name;
        this.quantity = quantity;
    }
    
    private void validateMenuItemId(Long menuItemId) {
        if (menuItemId == null) {
            throw new IllegalArgumentException("Menu item ID cannot be null");
        }
    }
    
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
    }
    
    private void validateQuantity(Integer quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public Long getMenuItemId() {
        return menuItemId;
    }
    
    public String getName() {
        return name;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    @Override
    public String toString() {
        return String.format("TicketLineItem[id=%d, menuItemId=%d, name='%s', quantity=%d]",
            id, menuItemId, name, quantity);
    }
}
