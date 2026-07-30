package net.ftgo.gateway.controller;

import net.ftgo.common.web.CorrelationIds;
import net.ftgo.common.web.FtgoProblemDetail;
import net.ftgo.gateway.handler.GatewayProblemResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;

/** Circuit-breaker fallbacks using the public RFC 9457 error contract. */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping(value = "/orders", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<FtgoProblemDetail> orderServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "order");
    }

    @RequestMapping(value = "/consumers", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<FtgoProblemDetail> consumerServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "consumer");
    }

    @RequestMapping(value = "/restaurants", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<FtgoProblemDetail> restaurantServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "restaurant");
    }

    @RequestMapping(value = "/tickets", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<FtgoProblemDetail> kitchenServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "kitchen");
    }

    @RequestMapping(value = "/accounts", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<FtgoProblemDetail> accountingServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "accounting");
    }

    @RequestMapping(value = "/deliveries", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<FtgoProblemDetail> deliveryServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "delivery");
    }

    @RequestMapping(value = "/order-history", method = RequestMethod.GET)
    public ResponseEntity<FtgoProblemDetail> orderHistoryServiceFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "order history");
    }

    private ResponseEntity<FtgoProblemDetail> buildFallbackResponse(
        ServerWebExchange exchange,
        String serviceName
    ) {
        String correlationId = GatewayProblemResponses.correlationId(exchange);
        FtgoProblemDetail body = new FtgoProblemDetail(
            URI.create("https://ftgo.example/problems/service-unavailable"),
            "Downstream service unavailable",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "The " + serviceName + " service is temporarily unavailable",
            URI.create(exchange.getRequest().getPath().value()),
            "SERVICE_UNAVAILABLE",
            correlationId
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .header(CorrelationIds.HEADER_NAME, correlationId)
            .body(body);
    }
}
