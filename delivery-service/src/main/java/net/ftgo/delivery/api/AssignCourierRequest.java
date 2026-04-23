package net.ftgo.delivery.api;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for assigning a courier to a delivery.
 */
public class AssignCourierRequest {
    
    @NotNull(message = "Courier ID is required")
    private Long courierId;
    
    public AssignCourierRequest() {
    }
    
    public AssignCourierRequest(Long courierId) {
        this.courierId = courierId;
    }
    
    public Long getCourierId() {
        return courierId;
    }
    
    public void setCourierId(Long courierId) {
        this.courierId = courierId;
    }
}
