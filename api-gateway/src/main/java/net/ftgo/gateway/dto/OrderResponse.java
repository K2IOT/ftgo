package net.ftgo.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order response from Order Service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String state;
    private Long consumerId;
    private Long restaurantId;
    private BigDecimal orderTotal;
    private List<LineItemResponse> lineItems;
    private String deliveryAddress;
    private LocalDateTime createdAt;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemResponse {
        private Long menuItemId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
    }
}
