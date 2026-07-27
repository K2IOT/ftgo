package net.ftgo.accounting.api.admin;

import net.ftgo.accounting.settlement.SettlementGatewayTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(assignableTypes = PaymentSettlementOperationsController.class)
public class PaymentSettlementExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException error) {
        return response(HttpStatus.BAD_REQUEST, error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> conflict(IllegalStateException error) {
        return response(HttpStatus.CONFLICT, error);
    }

    @ExceptionHandler(SettlementGatewayTimeoutException.class)
    public ResponseEntity<ErrorResponse> providerUnavailable(
        SettlementGatewayTimeoutException error
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, error);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, RuntimeException error) {
        return ResponseEntity.status(status).body(new ErrorResponse(
            status.value(),
            status.getReasonPhrase(),
            error.getMessage(),
            Instant.now()
        ));
    }

    public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp
    ) {
    }
}