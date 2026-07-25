package net.ftgo.orderhistory.api;

import net.ftgo.orderhistory.service.UnsupportedOrderHistoryQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderHistoryExceptionHandler {

    @ExceptionHandler(UnsupportedOrderHistoryQueryException.class)
    public ResponseEntity<OrderHistoryErrorResponse> unsupported(
        UnsupportedOrderHistoryQueryException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new OrderHistoryErrorResponse(exception.getErrorCode(), exception.getMessage())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<OrderHistoryErrorResponse> invalid(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new OrderHistoryErrorResponse("ORDER_HISTORY_QUERY_INVALID", exception.getMessage())
        );
    }
}
