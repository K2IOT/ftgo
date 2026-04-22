package net.ftgo.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

/**
 * Value object representing payment information for an order.
 * 
 * Contains the payment token used for credit card authorization.
 * The actual credit card details are not stored; only a tokenized reference.
 */
@Embeddable
public class PaymentInfo {
    
    @NotBlank(message = "Payment token is required")
    @Column(name = "payment_token", nullable = false)
    private String paymentToken;
    
    /**
     * Default constructor for JPA.
     */
    protected PaymentInfo() {
    }
    
    /**
     * Creates a new PaymentInfo.
     * 
     * @param paymentToken the payment token
     * @throws IllegalArgumentException if payment token is null or blank
     */
    public PaymentInfo(String paymentToken) {
        validatePaymentToken(paymentToken);
        this.paymentToken = paymentToken;
    }
    
    /**
     * Validates that the payment token is not null or blank.
     * 
     * @param paymentToken the payment token to validate
     * @throws IllegalArgumentException if payment token is null or blank
     */
    private void validatePaymentToken(String paymentToken) {
        if (paymentToken == null || paymentToken.isBlank()) {
            throw new IllegalArgumentException("Payment token cannot be null or blank");
        }
    }
    
    // Getter
    
    public String getPaymentToken() {
        return paymentToken;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentInfo that = (PaymentInfo) o;
        return Objects.equals(paymentToken, that.paymentToken);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(paymentToken);
    }
    
    @Override
    public String toString() {
        // Don't expose the full token in toString for security
        return String.format("PaymentInfo{token=***%s}", 
            paymentToken.length() > 4 ? paymentToken.substring(paymentToken.length() - 4) : "****");
    }
}
