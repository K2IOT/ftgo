package net.ftgo.accounting.api.admin;

import jakarta.servlet.http.HttpServletRequest;
import net.ftgo.accounting.settlement.SettlementGatewayTimeoutException;
import net.ftgo.accounting.settlement.SettlementRetryExhaustedException;
import net.ftgo.common.web.FtgoProblemDetail;
import net.ftgo.common.web.FtgoProblemResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PaymentSettlementOperationsController.class)
public class PaymentSettlementExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FtgoProblemDetail> badRequest(
        IllegalArgumentException error,
        HttpServletRequest request
    ) {
        return FtgoProblemResponses.response(
            HttpStatus.BAD_REQUEST,
            "payment-settlement-invalid",
            "Invalid payment settlement request",
            "The payment settlement request is invalid",
            "PAYMENT_SETTLEMENT_INVALID",
            request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<FtgoProblemDetail> conflict(
        IllegalStateException error,
        HttpServletRequest request
    ) {
        return FtgoProblemResponses.response(
            HttpStatus.CONFLICT,
            "payment-settlement-conflict",
            "Payment settlement conflict",
            "The payment settlement request conflicts with current state",
            "PAYMENT_SETTLEMENT_CONFLICT",
            request
        );
    }

    @ExceptionHandler({
        SettlementGatewayTimeoutException.class,
        SettlementRetryExhaustedException.class
    })
    public ResponseEntity<FtgoProblemDetail> providerUnavailable(
        RuntimeException error,
        HttpServletRequest request
    ) {
        return FtgoProblemResponses.response(
            HttpStatus.SERVICE_UNAVAILABLE,
            "payment-provider-unavailable",
            "Payment provider unavailable",
            "The payment provider is temporarily unavailable",
            "PAYMENT_PROVIDER_UNAVAILABLE",
            request
        );
    }
}
