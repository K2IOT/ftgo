package net.ftgo.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ticket response from Kitchen Service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long id;
    private Long orderId;
    private String state;
    private LocalDateTime acceptedAt;
    private LocalDateTime preparedAt;
    private LocalDateTime readyBy;
}
