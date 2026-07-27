package net.ftgo.common.web;

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
}
