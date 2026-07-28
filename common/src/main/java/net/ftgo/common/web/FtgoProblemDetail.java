package net.ftgo.common.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;

/** Stable RFC 9457-compatible error response with FTGO extensions. */
public record FtgoProblemDetail(
    URI type,
    String title,
    int status,
    String detail,
    URI instance,
    String errorCode,
    String correlationId
) {

    /** One-release compatibility alias for clients that previously read `error`. */
    @JsonProperty("error")
    public String legacyError() {
        return detail;
    }

    /** One-release compatibility alias for clients that previously read `message`. */
    @JsonProperty("message")
    public String legacyMessage() {
        return detail;
    }
}
