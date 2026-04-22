package net.ftgo.order.domain.events;

import net.ftgo.common.Money;
import net.ftgo.order.domain.OrderLineItem;

import java.util.List;

/**
 * Domain event published when an order is successfully revised.
 * 
 * This event is published by ReviseOrderSaga when the revision completes:
 * - Payment authorization has been adjusted to the new total
 * - Kitchen ticket has been updated with revised line items
 * - Order state has transitioned back to APPROVED with updated details
 * 
 * Consumers of this event:
 * - Order History Service: Updates read model to show revised order details
 * - Delivery Service: Updates delivery details if delivery address changed
 * - Analytics Service: Records revision metrics
 */
public class OrderRevised {
    
    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private List<OrderLineItem> revisedLineItems;
    private Money revisedTotal;
    
    /**
     * Default constructor for serialization.
     */
    public OrderRevised() {
    }
    
    /**
     * Creates an OrderRevised event.
     * 
     * @param orderId the revised order ID
     * @param consumerId the consumer who revised the order
     * @param restaurantId the restaurant for the revised order
     * @param revisedLineItems the new line items
     * @param revisedTotal the new order total
     */
    public OrderRevised(Long orderId, Long consumerId, Long restaurantId,
                       List<OrderLineItem> revisedLineItems, Money revisedTotal) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.revisedLineItems = revisedLineItems;
        this.revisedTotal = revisedTotal;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }
    
    public List<OrderLineItem> getRevisedLineItems() {
        return revisedLineItems;
    }
    
    public void setRevisedLineItems(List<OrderLineItem> revisedLineItems) {
        this.revisedLineItems = revisedLineItems;
    }
    
    public Money getRevisedTotal() {
        return revisedTotal;
    }
    
    public void setRevisedTotal(Money revisedTotal) {
        this.revisedTotal = revisedTotal;
    }
    
    @Override
    public String toString() {
        return String.format("OrderRevised{orderId=%d, consumerId=%d, restaurantId=%d, revisedTotal=%s}",
            orderId, consumerId, restaurantId, revisedTotal);
    }
}
