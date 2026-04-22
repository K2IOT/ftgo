package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.order.domain.OrderLineItem;

import java.util.List;

/**
 * Command sent to Kitchen Service to create a kitchen ticket.
 * 
 * Part of CreateOrderSaga step 3.
 * Kitchen Service creates a ticket in CREATE_PENDING state with the order line items.
 */
public class CreateTicketCommand implements Command {
    
    private Long orderId;
    private Long restaurantId;
    private List<OrderLineItem> lineItems;
    
    /**
     * Default constructor for serialization.
     */
    public CreateTicketCommand() {
    }
    
    /**
     * Creates a create ticket command.
     * 
     * @param orderId the order ID
     * @param restaurantId the restaurant ID
     * @param lineItems the order line items
     */
    public CreateTicketCommand(Long orderId, Long restaurantId, List<OrderLineItem> lineItems) {
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
    
    public List<OrderLineItem> getLineItems() {
        return lineItems;
    }
    
    public void setLineItems(List<OrderLineItem> lineItems) {
        this.lineItems = lineItems;
    }
}
