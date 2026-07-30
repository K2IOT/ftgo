package net.ftgo.orderhistory.api;

import jakarta.servlet.http.HttpServletRequest;
import net.ftgo.common.web.FtgoProblemDetail;
import net.ftgo.common.web.FtgoProblemResponses;
import net.ftgo.orderhistory.service.UnsupportedOrderHistoryQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

@RestControllerAdvice
public class OrderHistoryExceptionHandler {

    @ExceptionHandler(UnsupportedOrderHistoryQueryException.class)
    public ResponseEntity<FtgoProblemDetail> unsupported(
        UnsupportedOrderHistoryQueryException exception,
        HttpServletRequest request
    ) {
        return invalidResponse(exception.getErrorCode(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FtgoProblemDetail> invalid(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        return invalidResponse("ORDER_HISTORY_QUERY_INVALID", request);
    }

    private ResponseEntity<FtgoProblemDetail> invalidResponse(
        String errorCode,
        HttpServletRequest request
    ) {
        String stableCode = errorCode == null || errorCode.isBlank()
            ? "ORDER_HISTORY_QUERY_INVALID"
            : errorCode;
        String type = stableCode.toLowerCase(Locale.ROOT).replace('_', '-');
        return FtgoProblemResponses.response(
            HttpStatus.BAD_REQUEST,
            type,
            "Invalid order history query",
            "The order history query is invalid",
            stableCode,
            request
        );
    }
}
