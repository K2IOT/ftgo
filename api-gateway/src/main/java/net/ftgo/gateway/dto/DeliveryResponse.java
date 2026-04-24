package net.ftgo.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Delivery response from Delivery Service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResponse {
    private Long id;
    private Long orderId;
    private String status;
    private Long courierId;
    private LocalDateTime scheduledTime;
    private LocalDateTime pickupTime;
    private LocalDateTime deliveryTime;
}
