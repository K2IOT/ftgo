package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.common.Command;

import java.util.List;

/**
 * Command to create a ticket (from CreateOrderSaga).
 */
public class CreateTicketCommand implements Command {
    
    private Long orderId;
    private Long restaurantId;
    private List<TicketLineItemDTO> lineItems;
    
    public CreateTicketCommand() {
    }
    
    public CreateTicketCommand(Long orderId, Long restaurantId, List<TicketLineItemDTO> lineItems) {
        this.orderId = orderId;
        this.restaurantId = restaurantId;
        this.lineItems = lineItems;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }
    
    public List<TicketLineItemDTO> getLineItems() {
        return lineItems;
    }
    
    public void setLineItems(List<TicketLineItemDTO> lineItems) {
        this.lineItems = lineItems;
    }
    
    /**
     * DTO for ticket line items in commands.
     */
    public static class TicketLineItemDTO {
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
}
