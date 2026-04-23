package net.ftgo.kitchen.api;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.domain.TicketState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO for Ticket responses.
 */
public class TicketDTO {
    
    private Long id;
    private Long restaurantId;
    private Long orderId;
    private TicketState state;
    private List<TicketLineItemDTO> lineItems;
    private LocalDateTime readyBy;
    private LocalDateTime acceptedAt;
    private LocalDateTime preparedAt;
    private LocalDateTime createdAt;
    
    public TicketDTO() {
    }
    
    public TicketDTO(Ticket ticket) {
        this.id = ticket.getId();
        this.restaurantId = ticket.getRestaurantId();
        this.orderId = ticket.getOrderId();
        this.state = ticket.getState();
        this.lineItems = ticket.getLineItems().stream()
            .map(TicketLineItemDTO::new)
            .collect(Collectors.toList());
        this.readyBy = ticket.getReadyBy();
        this.acceptedAt = ticket.getAcceptedAt();
        this.preparedAt = ticket.getPreparedAt();
        this.createdAt = ticket.getCreatedAt();
    }
    
    // Getters and setters
    
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
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public TicketState getState() {
        return state;
    }
    
    public void setState(TicketState state) {
        this.state = state;
    }
    
    public List<TicketLineItemDTO> getLineItems() {
        return lineItems;
    }
    
    public void setLineItems(List<TicketLineItemDTO> lineItems) {
        this.lineItems = lineItems;
    }
    
    public LocalDateTime getReadyBy() {
        return readyBy;
    }
    
    public void setReadyBy(LocalDateTime readyBy) {
        this.readyBy = readyBy;
    }
    
    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }
    
    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
    
    public LocalDateTime getPreparedAt() {
        return preparedAt;
    }
    
    public void setPreparedAt(LocalDateTime preparedAt) {
        this.preparedAt = preparedAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * DTO for ticket line items.
     */
    public static class TicketLineItemDTO {
        private Long id;
        private Long menuItemId;
        private String name;
        private Integer quantity;
        
        public TicketLineItemDTO() {
        }
        
        public TicketLineItemDTO(TicketLineItem lineItem) {
            this.id = lineItem.getId();
            this.menuItemId = lineItem.getMenuItemId();
            this.name = lineItem.getName();
            this.quantity = lineItem.getQuantity();
        }
        
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
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
