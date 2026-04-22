package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error response DTO.
 * 
 * Contains error code and message for API error responses.
 */
public class ErrorResponse {
    
    @JsonProperty
    private final String errorCode;
    
    @JsonProperty
    private final String message;
    
    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getMessage() {
        return message;
    }
}
