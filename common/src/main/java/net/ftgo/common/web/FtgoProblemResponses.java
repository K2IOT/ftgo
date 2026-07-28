package net.ftgo.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/** Shared factory for stable, correlated RFC 9457 responses. */
public final class FtgoProblemResponses {

    private static final String TYPE_BASE = "https://ftgo.example/problems/";

    private FtgoProblemResponses() {
    }

    public static ResponseEntity<FtgoProblemDetail> response(
        HttpStatus status,
        String type,
        String title,
        String detail,
        String errorCode,
        HttpServletRequest request
    ) {
        String correlationId = CorrelationIdFilter.current(request);
        FtgoProblemDetail body = new FtgoProblemDetail(
            URI.create(TYPE_BASE + type),
            title,
            status.value(),
            detail,
            URI.create(request.getRequestURI()),
            errorCode,
            correlationId
        );
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .header(CorrelationIdFilter.HEADER_NAME, correlationId)
            .body(body);
    }
}
