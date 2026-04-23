package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * User-defined type representing an order line item in ScyllaDB.
 * 
 * Stored as a frozen UDT within the order_history table's line_items list.
 */
@UserDefinedType("line_item")
public class LineItem {
    
    private Long menuItemId;
    private String name;
    private BigDecimal price;
    private Integer quantity;
    
    /**
     * Default constructor for Cassandra mapping.
     */
    public LineItem() {
    }
    
    /**
     * Creates a new LineItem.
     * 
     * @param menuItemId the menu item ID
     * @param name the item name
     * @param price the item price
     * @param quantity the quantity ordered
     */
    public LineItem(Long menuItemId, String name, BigDecimal price, Integer quantity) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    
    public Long getMenuItemId() {
        return menuItemId;
    }
    
    public void setMenuItemId(Long menuItemId) {
        this.menuItemId = menuItemId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LineItem lineItem = (LineItem) o;
        return Objects.equals(menuItemId, lineItem.menuItemId) &&
               Objects.equals(name, lineItem.name) &&
               Objects.equals(price, lineItem.price) &&
               Objects.equals(quantity, lineItem.quantity);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(menuItemId, name, price, quantity);
    }
    
    @Override
    public String toString() {
        return "LineItem{" +
               "menuItemId=" + menuItemId +
               ", name='" + name + '\'' +
               ", price=" + price +
               ", quantity=" + quantity +
               '}';
    }
}
