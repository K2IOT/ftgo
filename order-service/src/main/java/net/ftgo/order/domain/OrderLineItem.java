package net.ftgo.order.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.ftgo.common.Money;

import java.util.Objects;

/**
 * Entity representing a line item in an order.
 * 
 * Each line item corresponds to a menu item from a restaurant with quantity and price.
 */
@Entity
@Table(name = "order_line_items")
public class OrderLineItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Order ID is required")
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    
    @NotNull(message = "Menu item ID is required")
    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;
    
    @NotBlank(message = "Item name is required")
    @Column(nullable = false)
    private String name;
    
    @NotNull(message = "Price is required")
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false, precision = 10, scale = 2))
    })
    private Money price;
    
    @Positive(message = "Quantity must be positive")
    @Column(nullable = false)
    private int quantity;
    
    /**
     * Default constructor for JPA.
     */
    protected OrderLineItem() {
    }
    
    /**
     * Creates a new OrderLineItem.
     * 
     * @param menuItemId the menu item ID
     * @param name the item name
     * @param price the item price
     * @param quantity the quantity ordered
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public OrderLineItem(Long menuItemId, String name, Money price, int quantity) {
        validateMenuItemId(menuItemId);
        validateName(name);
        validatePrice(price);
        validateQuantity(quantity);
        
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    
    /**
     * Validates that the menu item ID is not null.
     * 
     * @param menuItemId the menu item ID to validate
     * @throws IllegalArgumentException if menuItemId is null
     */
    private void validateMenuItemId(Long menuItemId) {
        if (menuItemId == null) {
            throw new IllegalArgumentException("Menu item ID cannot be null");
        }
    }
    
    /**
     * Validates that the item name is not null or blank.
     * 
     * @param name the name to validate
     * @throws IllegalArgumentException if name is null or blank
     */
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be null or blank");
        }
    }
    
    /**
     * Validates that the price is not null and is positive.
     * 
     * @param price the price to validate
     * @throws IllegalArgumentException if price is null or not positive
     */
    private void validatePrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }
        if (price.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }
    
    /**
     * Validates that the quantity is positive.
     * 
     * @param quantity the quantity to validate
     * @throws IllegalArgumentException if quantity is not positive
     */
    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
    
    /**
     * Calculates the total price for this line item (price * quantity).
     * 
     * @return the total price
     */
    public Money getTotal() {
        return price.multiply(quantity);
    }
    
    /**
     * Sets the order ID for this line item.
     * Used by the Order aggregate when adding line items.
     * 
     * @param orderId the order ID
     */
    void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public Long getMenuItemId() {
        return menuItemId;
    }
    
    public String getName() {
        return name;
    }
    
    public Money getPrice() {
        return price;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderLineItem that = (OrderLineItem) o;
        return quantity == that.quantity &&
                Objects.equals(menuItemId, that.menuItemId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(price, that.price);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(menuItemId, name, price, quantity);
    }
    
    @Override
    public String toString() {
        return String.format("OrderLineItem{menuItemId=%d, name='%s', price=%s, quantity=%d}", 
            menuItemId, name, price, quantity);
    }
}
