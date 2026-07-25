package net.ftgo.accounting.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/webhooks/payments")
public class PaymentWebhookController {

    private final PaymentWebhookSignatureVerifier signatureVerifier;
    private final PaymentWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookController(
        PaymentWebhookSignatureVerifier signatureVerifier,
        PaymentWebhookService webhookService,
        ObjectMapper objectMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<WebhookResponse> receive(
        @PathVariable String provider,
        @RequestHeader("X-Payment-Timestamp") long timestamp,
        @RequestHeader("X-Payment-Signature") String signature,
        @RequestBody byte[] rawBody
    ) {
        signatureVerifier.verify(provider, timestamp, signature, rawBody);
        PaymentWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, PaymentWebhookPayload.class);
        } catch (Exception e) {
            throw new InvalidPaymentWebhookException("Invalid payment webhook JSON", e);
        }
        PaymentWebhookApplyResult result = webhookService.apply(
            provider,
            payload,
            sha256(rawBody)
        );
        return ResponseEntity.ok(new WebhookResponse(result));
    }

    @ExceptionHandler(InvalidPaymentWebhookException.class)
    public ResponseEntity<WebhookError> invalidWebhook(
        InvalidPaymentWebhookException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new WebhookError("INVALID_PAYMENT_WEBHOOK"));
    }

    private String sha256(byte[] rawBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawBody);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record WebhookResponse(PaymentWebhookApplyResult result) {
    }

    public record WebhookError(String code) {
    }
}
