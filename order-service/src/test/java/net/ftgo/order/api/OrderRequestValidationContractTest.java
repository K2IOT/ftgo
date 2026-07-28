package net.ftgo.order.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestValidationContractTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsBoundedValidRequest() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void requiresPositiveRestaurantId() {
        CreateOrderRequest request = request(0L, List.of(validLineItem()), futureTime(), validAddress());

        assertViolation(request, "restaurantId");
    }

    @Test
    void limitsOrderToFiftyItems() {
        CreateOrderRequest request = request(
            1L,
            Collections.nCopies(51, validLineItem()),
            futureTime(),
            validAddress()
        );

        assertViolation(request, "lineItems");
    }

    @Test
    void requiresFutureDeliveryTime() {
        CreateOrderRequest request = request(
            1L,
            List.of(validLineItem()),
            LocalDateTime.now().minusMinutes(1),
            validAddress()
        );

        assertViolation(request, "deliveryTime");
    }

    @Test
    void limitsQuantityAndTextFields() {
        OrderLineItemRequest lineItem = new OrderLineItemRequest(
            10L,
            "x".repeat(201),
            new Money(new BigDecimal("12.99")),
            101
        );
        CreateOrderRequest request = request(
            1L,
            List.of(lineItem),
            futureTime(),
            new Address("x".repeat(201), "Bangkok", "BK", "10110")
        );

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
            .contains("lineItems[0].name", "lineItems[0].quantity", "deliveryAddress.street");
    }

    private static CreateOrderRequest validRequest() {
        return request(1L, List.of(validLineItem()), futureTime(), validAddress());
    }

    private static CreateOrderRequest request(
        Long restaurantId,
        List<OrderLineItemRequest> lineItems,
        LocalDateTime deliveryTime,
        Address address
    ) {
        return new CreateOrderRequest(
            restaurantId,
            1L,
            lineItems,
            address,
            deliveryTime,
            "tok_validation"
        );
    }

    private static OrderLineItemRequest validLineItem() {
        return new OrderLineItemRequest(
            10L,
            "Burger",
            new Money(new BigDecimal("12.99")),
            1
        );
    }

    private static Address validAddress() {
        return new Address("1 Main Street", "Bangkok", "BK", "10110");
    }

    private static LocalDateTime futureTime() {
        return LocalDateTime.now().plusHours(1);
    }

    private static void assertViolation(CreateOrderRequest request, String property) {
        assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains(property);
    }
}
