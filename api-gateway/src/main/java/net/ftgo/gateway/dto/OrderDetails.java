package net.ftgo.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated order details from multiple services.
 * Combines data from Order Service, Kitchen Service, and Delivery Service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetails {
    
    // Order Service data
    private Long orderId;
    private String orderState;
    private Long consumerId;
    private Long restaurantId;
    private BigDecimal orderTotal;
    private List<LineItem> lineItems;
    private String deliveryAddress;
    private LocalDateTime createdAt;
    
    // Kitchen Service data
    private TicketInfo ticketInfo;
    
    // Delivery Service data
    private DeliveryInfo deliveryInfo;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private Long menuItemId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketInfo {
        private Long ticketId;
        private String ticketState;
        private LocalDateTime acceptedAt;
        private LocalDateTime preparedAt;
        private LocalDateTime readyBy;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfo {
        private Long deliveryId;
        private String deliveryStatus;
        private Long courierId;
        private LocalDateTime scheduledTime;
        private LocalDateTime pickupTime;
        private LocalDateTime deliveryTime;
    }
}
