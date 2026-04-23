package net.ftgo.kitchen.messaging;

/**
 * DTO for ticket line items used in commands.
 * Shared across all command types.
 */
public class TicketLineItemDTO {
    private Long menuItemId;
    private String name;
    private Integer quantity;
    
    public TicketLineItemDTO() {
    }
    
    public TicketLineItemDTO(Long menuItemId, String name, Integer quantity) {
        this.menuItemId = menuItemId;
        this.name = name;
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
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
