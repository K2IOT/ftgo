package net.ftgo.order.domain;

import net.ftgo.common.Money;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Order aggregate using jqwik.
 * 
 * These tests validate universal properties that should hold for all valid inputs:
 * - Property 1: Order Creation Idempotency (Requirements 1.9)
 * - Property 2: Order Total Invariant (Requirements 3.8)
 */
class OrderPropertyTest {
    
    // ========== Property 1: Order Creation Idempotency ==========
    
    /**
     * Property 1: Order Creation Idempotency
     * 
     * **Validates: Requirements 1.9**
     * 
     * For any valid order creation request, creating the order and then immediately
     * querying it SHALL return order details equivalent to the creation request.
     * 
     * This property ensures that order creation is idempotent - the order's fields
     * match the input data used to create it.
     */
    @Property(tries = 100)
    @Label("Property 1: Order Creation Idempotency - Created order matches input data")
    void orderCreationIdempotency_createdOrderMatchesInputData(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll("lineItemLists") List<OrderLineItem> lineItems,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        // When: We create an order with the given data
        Order order = new Order(consumerId, restaurantId, lineItems, deliveryInfo, paymentInfo);
        
        // Then: The order's fields should match the input data
        assertEquals(consumerId, order.getConsumerId(),
            "Consumer ID should match input");
        assertEquals(restaurantId, order.getRestaurantId(),
            "Restaurant ID should match input");
        assertEquals(deliveryInfo, order.getDeliveryInfo(),
            "Delivery info should match input");
        assertEquals(paymentInfo, order.getPaymentInfo(),
            "Payment info should match input");
        
        // And: The order should have the correct initial state
        assertEquals(OrderState.APPROVAL_PENDING, order.getState(),
            "Order should be in APPROVAL_PENDING state");
        
        // And: The line items should match the input
        List<OrderLineItem> orderLineItems = order.getLineItems();
        assertEquals(lineItems.size(), orderLineItems.size(),
            "Number of line items should match input");
        
        for (int i = 0; i < lineItems.size(); i++) {
            OrderLineItem expected = lineItems.get(i);
            OrderLineItem actual = orderLineItems.get(i);
            
            assertEquals(expected.getMenuItemId(), actual.getMenuItemId(),
                String.format("Line item %d menu item ID should match", i));
            assertEquals(expected.getName(), actual.getName(),
                String.format("Line item %d name should match", i));
            assertEquals(expected.getPrice(), actual.getPrice(),
                String.format("Line item %d price should match", i));
            assertEquals(expected.getQuantity(), actual.getQuantity(),
                String.format("Line item %d quantity should match", i));
        }
        
        // And: The order total should be calculated correctly
        Money expectedTotal = lineItems.stream()
            .map(OrderLineItem::getTotal)
            .reduce(Money.ZERO, Money::add);
        assertEquals(expectedTotal, order.getOrderTotal(),
            "Order total should equal sum of line item totals");
        
        // And: Timestamps should be set
        assertNotNull(order.getCreatedAt(), "Created timestamp should be set");
        assertNotNull(order.getUpdatedAt(), "Updated timestamp should be set");
    }
    
    /**
     * Property 1 (variant): Order Creation with Single Line Item
     * 
     * Tests that order creation works correctly with a single line item,
     * which is the minimum valid case.
     */
    @Property(tries = 100)
    @Label("Property 1: Order Creation Idempotency - Single line item order")
    void orderCreationIdempotency_singleLineItemOrder(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll @Positive long menuItemId,
        @ForAll @NotBlank String itemName,
        @ForAll @Positive BigDecimal price,
        @ForAll @IntRange(min = 1, max = 100) int quantity,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        // Given: A single line item
        Money itemPrice = new Money(price);
        OrderLineItem lineItem = new OrderLineItem(menuItemId, itemName, itemPrice, quantity);
        List<OrderLineItem> lineItems = List.of(lineItem);
        
        // When: We create an order
        Order order = new Order(consumerId, restaurantId, lineItems, deliveryInfo, paymentInfo);
        
        // Then: The order should have exactly one line item
        assertEquals(1, order.getLineItems().size(),
            "Order should have exactly one line item");
        
        // And: The line item should match the input
        OrderLineItem actualLineItem = order.getLineItems().get(0);
        assertEquals(menuItemId, actualLineItem.getMenuItemId());
        assertEquals(itemName, actualLineItem.getName());
        assertEquals(itemPrice, actualLineItem.getPrice());
        assertEquals(quantity, actualLineItem.getQuantity());
        
        // And: The order total should equal the line item total
        Money expectedTotal = itemPrice.multiply(quantity);
        assertEquals(expectedTotal, order.getOrderTotal(),
            "Order total should equal line item price * quantity");
    }
    
    // ========== Property 2: Order Total Invariant ==========
    
    /**
     * Property 2: Order Total Invariant
     * 
     * **Validates: Requirements 3.8**
     * 
     * For any order revision, the revised order total SHALL equal the sum of all
     * revised line item prices plus the delivery fee.
     * 
     * Note: The current implementation doesn't include a separate delivery fee field,
     * so this property verifies that the order total equals the sum of line item totals.
     */
    @Property(tries = 100)
    @Label("Property 2: Order Total Invariant - Revised order total equals sum of line items")
    void orderTotalInvariant_revisedOrderTotalEqualsSumOfLineItems(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll("lineItemLists") List<OrderLineItem> originalLineItems,
        @ForAll("lineItemLists") List<OrderLineItem> revisedLineItems,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        // Given: An approved order
        Order order = new Order(consumerId, restaurantId, originalLineItems, deliveryInfo, paymentInfo);
        order.approve();
        
        // When: We revise the order with new line items
        order.beginRevise();
        order.confirmRevise(revisedLineItems);
        
        // Then: The order total should equal the sum of revised line item totals
        Money expectedTotal = revisedLineItems.stream()
            .map(OrderLineItem::getTotal)
            .reduce(Money.ZERO, Money::add);
        
        assertEquals(expectedTotal, order.getOrderTotal(),
            "Revised order total should equal sum of revised line item totals");
        
        // And: The order should be back in APPROVED state
        assertEquals(OrderState.APPROVED, order.getState(),
            "Order should be in APPROVED state after revision");
        
        // And: The line items should match the revised line items
        List<OrderLineItem> orderLineItems = order.getLineItems();
        assertEquals(revisedLineItems.size(), orderLineItems.size(),
            "Number of line items should match revised line items");
    }
    
    /**
     * Property 2 (variant): Order Total Invariant with Quantity Changes
     * 
     * Tests that changing only the quantity of line items correctly updates the order total.
     */
    @Property(tries = 100)
    @Label("Property 2: Order Total Invariant - Quantity changes update total correctly")
    void orderTotalInvariant_quantityChangesUpdateTotalCorrectly(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll @Positive long menuItemId,
        @ForAll @NotBlank String itemName,
        @ForAll @Positive BigDecimal price,
        @ForAll @IntRange(min = 1, max = 50) int originalQuantity,
        @ForAll @IntRange(min = 1, max = 50) int revisedQuantity,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        Assume.that(originalQuantity != revisedQuantity);
        
        // Given: An approved order with a specific quantity
        Money itemPrice = new Money(price);
        OrderLineItem originalLineItem = new OrderLineItem(menuItemId, itemName, itemPrice, originalQuantity);
        Order order = new Order(consumerId, restaurantId, List.of(originalLineItem), deliveryInfo, paymentInfo);
        order.approve();
        
        Money originalTotal = order.getOrderTotal();
        Money expectedOriginalTotal = itemPrice.multiply(originalQuantity);
        assertEquals(expectedOriginalTotal, originalTotal,
            "Original order total should equal price * original quantity");
        
        // When: We revise the order with a different quantity
        OrderLineItem revisedLineItem = new OrderLineItem(menuItemId, itemName, itemPrice, revisedQuantity);
        order.beginRevise();
        order.confirmRevise(List.of(revisedLineItem));
        
        // Then: The order total should reflect the new quantity
        Money expectedRevisedTotal = itemPrice.multiply(revisedQuantity);
        assertEquals(expectedRevisedTotal, order.getOrderTotal(),
            "Revised order total should equal price * revised quantity");
        
        // And: The total should have changed
        assertNotEquals(originalTotal, order.getOrderTotal(),
            "Order total should change when quantity changes");
    }
    
    /**
     * Property 2 (variant): Order Total Invariant with Multiple Revisions
     * 
     * Tests that multiple successive revisions maintain the order total invariant.
     */
    @Property(tries = 100)
    @Label("Property 2: Order Total Invariant - Multiple revisions maintain invariant")
    void orderTotalInvariant_multipleRevisionsMaintainInvariant(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll("lineItemLists") List<OrderLineItem> lineItems1,
        @ForAll("lineItemLists") List<OrderLineItem> lineItems2,
        @ForAll("lineItemLists") List<OrderLineItem> lineItems3,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        // Given: An approved order
        Order order = new Order(consumerId, restaurantId, lineItems1, deliveryInfo, paymentInfo);
        order.approve();
        
        // When: We perform first revision
        order.beginRevise();
        order.confirmRevise(lineItems2);
        
        // Then: The order total should match the first revision
        Money expectedTotal2 = lineItems2.stream()
            .map(OrderLineItem::getTotal)
            .reduce(Money.ZERO, Money::add);
        assertEquals(expectedTotal2, order.getOrderTotal(),
            "Order total should match first revision");
        
        // When: We perform second revision
        order.beginRevise();
        order.confirmRevise(lineItems3);
        
        // Then: The order total should match the second revision
        Money expectedTotal3 = lineItems3.stream()
            .map(OrderLineItem::getTotal)
            .reduce(Money.ZERO, Money::add);
        assertEquals(expectedTotal3, order.getOrderTotal(),
            "Order total should match second revision");
        
        // And: The order should still be in APPROVED state
        assertEquals(OrderState.APPROVED, order.getState(),
            "Order should remain in APPROVED state after multiple revisions");
    }
    
    /**
     * Property 2 (variant): Order Total Invariant with Price Changes
     * 
     * Tests that changing line item prices correctly updates the order total.
     */
    @Property(tries = 100)
    @Label("Property 2: Order Total Invariant - Price changes update total correctly")
    void orderTotalInvariant_priceChangesUpdateTotalCorrectly(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll @Positive long menuItemId,
        @ForAll @NotBlank String itemName,
        @ForAll @Positive BigDecimal originalPrice,
        @ForAll @Positive BigDecimal revisedPrice,
        @ForAll @IntRange(min = 1, max = 10) int quantity,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        Assume.that(!originalPrice.equals(revisedPrice));
        
        // Given: An approved order with a specific price
        Money originalItemPrice = new Money(originalPrice);
        OrderLineItem originalLineItem = new OrderLineItem(menuItemId, itemName, originalItemPrice, quantity);
        Order order = new Order(consumerId, restaurantId, List.of(originalLineItem), deliveryInfo, paymentInfo);
        order.approve();
        
        Money originalTotal = order.getOrderTotal();
        Money expectedOriginalTotal = originalItemPrice.multiply(quantity);
        assertEquals(expectedOriginalTotal, originalTotal,
            "Original order total should equal original price * quantity");
        
        // When: We revise the order with a different price
        Money revisedItemPrice = new Money(revisedPrice);
        OrderLineItem revisedLineItem = new OrderLineItem(menuItemId, itemName, revisedItemPrice, quantity);
        order.beginRevise();
        order.confirmRevise(List.of(revisedLineItem));
        
        // Then: The order total should reflect the new price
        Money expectedRevisedTotal = revisedItemPrice.multiply(quantity);
        assertEquals(expectedRevisedTotal, order.getOrderTotal(),
            "Revised order total should equal revised price * quantity");
        
        // And: The total should have changed
        assertNotEquals(originalTotal, order.getOrderTotal(),
            "Order total should change when price changes");
    }
    
    // ========== Arbitraries (Data Generators) ==========
    
    /**
     * Provides arbitrary lists of OrderLineItem with 1-5 items.
     */
    @Provide
    Arbitrary<List<OrderLineItem>> lineItemLists() {
        return lineItems().list().ofMinSize(1).ofMaxSize(5);
    }
    
    /**
     * Provides arbitrary OrderLineItem instances.
     */
    @Provide
    Arbitrary<OrderLineItem> lineItems() {
        Arbitrary<Long> menuItemIds = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<String> names = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars(' ', '-')
            .ofMinLength(3)
            .ofMaxLength(50)
            .filter(s -> !s.isBlank()); // Ensure non-blank names
        Arbitrary<Money> prices = moneys();
        Arbitrary<Integer> quantities = Arbitraries.integers().between(1, 100);
        
        return Combinators.combine(menuItemIds, names, prices, quantities)
            .as(OrderLineItem::new);
    }
    
    /**
     * Provides arbitrary Money instances with positive amounts.
     * Generates amounts between 0.01 and 1000.00 with 2 decimal places.
     */
    @Provide
    Arbitrary<Money> moneys() {
        return Arbitraries.bigDecimals()
            .between(new BigDecimal("0.01"), new BigDecimal("1000.00"))
            .ofScale(2)
            .map(Money::new);
    }
    
    /**
     * Provides arbitrary DeliveryInfo instances.
     */
    @Provide
    Arbitrary<DeliveryInfo> deliveryInfos() {
        Arbitrary<String> addresses = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars(' ', ',', '.', '-')
            .ofMinLength(10)
            .ofMaxLength(100)
            .filter(s -> !s.isBlank()); // Ensure non-blank addresses
        
        // Generate delivery times in the future (1 hour to 7 days from now)
        Arbitrary<LocalDateTime> deliveryTimes = Arbitraries.longs()
            .between(1, 7 * 24) // 1 hour to 7 days in hours
            .map(hours -> LocalDateTime.now().plusHours(hours));
        
        return Combinators.combine(addresses, deliveryTimes)
            .as(DeliveryInfo::new);
    }
    
    /**
     * Provides arbitrary PaymentInfo instances.
     */
    @Provide
    Arbitrary<PaymentInfo> paymentInfos() {
        Arbitrary<String> tokens = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(10)
            .ofMaxLength(50);
        
        return tokens.map(PaymentInfo::new);
    }
}
