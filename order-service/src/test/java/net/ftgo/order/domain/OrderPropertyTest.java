package net.ftgo.order.domain;

import net.ftgo.common.Money;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Property-based tests for valid Order aggregate inputs. */
class OrderPropertyTest {

    @Property(tries = 100)
    @Label("Property 1: Order Creation Idempotency - Created order matches input data")
    void orderCreationIdempotency_createdOrderMatchesInputData(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll("lineItemLists") List<OrderLineItem> lineItems,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        Order order = new Order(consumerId, restaurantId, lineItems, deliveryInfo, paymentInfo);

        assertEquals(consumerId, order.getConsumerId());
        assertEquals(restaurantId, order.getRestaurantId());
        assertEquals(deliveryInfo, order.getDeliveryInfo());
        assertEquals(paymentInfo, order.getPaymentInfo());
        assertEquals(OrderState.APPROVAL_PENDING, order.getState());
        assertEquals(lineItems.size(), order.getLineItems().size());
        for (int i = 0; i < lineItems.size(); i++) {
            OrderLineItem expected = lineItems.get(i);
            OrderLineItem actual = order.getLineItems().get(i);
            assertEquals(expected.getMenuItemId(), actual.getMenuItemId());
            assertEquals(expected.getName(), actual.getName());
            assertEquals(expected.getPrice(), actual.getPrice());
            assertEquals(expected.getQuantity(), actual.getQuantity());
        }
        assertEquals(total(lineItems), order.getOrderTotal());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
    }

    @Property(tries = 100)
    @Label("Property 1: Order Creation Idempotency - Single line item order")
    void orderCreationIdempotency_singleLineItemOrder(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll @Positive long menuItemId,
        @ForAll("itemNames") String itemName,
        @ForAll("prices") BigDecimal price,
        @ForAll @IntRange(min = 1, max = 100) int quantity,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        Money itemPrice = new Money(price);
        OrderLineItem lineItem = new OrderLineItem(menuItemId, itemName, itemPrice, quantity);
        Order order = new Order(
            consumerId,
            restaurantId,
            List.of(lineItem),
            deliveryInfo,
            paymentInfo
        );

        assertEquals(1, order.getLineItems().size());
        OrderLineItem actual = order.getLineItems().get(0);
        assertEquals(menuItemId, actual.getMenuItemId());
        assertEquals(itemName, actual.getName());
        assertEquals(itemPrice, actual.getPrice());
        assertEquals(quantity, actual.getQuantity());
        assertEquals(itemPrice.multiply(quantity), order.getOrderTotal());
    }

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
        Order order = new Order(
            consumerId,
            restaurantId,
            originalLineItems,
            deliveryInfo,
            paymentInfo
        );
        order.approve();
        order.beginRevise();
        order.confirmRevise(revisedLineItems);

        assertEquals(total(revisedLineItems), order.getOrderTotal());
        assertEquals(OrderState.APPROVED, order.getState());
        assertEquals(revisedLineItems.size(), order.getLineItems().size());
    }

    @Property(tries = 100)
    @Label("Property 2: Order Total Invariant - Quantity changes update total correctly")
    void orderTotalInvariant_quantityChangesUpdateTotalCorrectly(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll @Positive long menuItemId,
        @ForAll("itemNames") String itemName,
        @ForAll("prices") BigDecimal price,
        @ForAll @IntRange(min = 1, max = 50) int originalQuantity,
        @ForAll @IntRange(min = 1, max = 50) int revisedQuantity,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        Assume.that(originalQuantity != revisedQuantity);
        Money itemPrice = new Money(price);
        Order order = new Order(
            consumerId,
            restaurantId,
            List.of(new OrderLineItem(menuItemId, itemName, itemPrice, originalQuantity)),
            deliveryInfo,
            paymentInfo
        );
        order.approve();
        Money originalTotal = order.getOrderTotal();

        order.beginRevise();
        order.confirmRevise(List.of(
            new OrderLineItem(menuItemId, itemName, itemPrice, revisedQuantity)
        ));

        assertEquals(itemPrice.multiply(revisedQuantity), order.getOrderTotal());
        assertNotEquals(originalTotal, order.getOrderTotal());
    }

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
        Order order = new Order(
            consumerId,
            restaurantId,
            lineItems1,
            deliveryInfo,
            paymentInfo
        );
        order.approve();

        order.beginRevise();
        order.confirmRevise(lineItems2);
        assertEquals(total(lineItems2), order.getOrderTotal());

        order.beginRevise();
        order.confirmRevise(lineItems3);
        assertEquals(total(lineItems3), order.getOrderTotal());
        assertEquals(OrderState.APPROVED, order.getState());
    }

    @Property(tries = 100)
    @Label("Property 2: Order Total Invariant - Price changes update total correctly")
    void orderTotalInvariant_priceChangesUpdateTotalCorrectly(
        @ForAll @Positive long consumerId,
        @ForAll @Positive long restaurantId,
        @ForAll @Positive long menuItemId,
        @ForAll("itemNames") String itemName,
        @ForAll("prices") BigDecimal originalPrice,
        @ForAll("prices") BigDecimal revisedPrice,
        @ForAll @IntRange(min = 1, max = 10) int quantity,
        @ForAll("deliveryInfos") DeliveryInfo deliveryInfo,
        @ForAll("paymentInfos") PaymentInfo paymentInfo
    ) {
        Assume.that(originalPrice.compareTo(revisedPrice) != 0);
        Money originalItemPrice = new Money(originalPrice);
        Order order = new Order(
            consumerId,
            restaurantId,
            List.of(new OrderLineItem(menuItemId, itemName, originalItemPrice, quantity)),
            deliveryInfo,
            paymentInfo
        );
        order.approve();
        Money originalTotal = order.getOrderTotal();

        Money revisedItemPrice = new Money(revisedPrice);
        order.beginRevise();
        order.confirmRevise(List.of(
            new OrderLineItem(menuItemId, itemName, revisedItemPrice, quantity)
        ));

        assertEquals(revisedItemPrice.multiply(quantity), order.getOrderTotal());
        assertNotEquals(originalTotal, order.getOrderTotal());
    }

    private Money total(List<OrderLineItem> lineItems) {
        return lineItems.stream()
            .map(OrderLineItem::getTotal)
            .reduce(Money.ZERO, Money::add);
    }

    @Provide
    Arbitrary<List<OrderLineItem>> lineItemLists() {
        return lineItems().list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<OrderLineItem> lineItems() {
        return Combinators.combine(
            Arbitraries.longs().between(1L, 10_000L),
            itemNames(),
            moneys(),
            Arbitraries.integers().between(1, 100)
        ).as(OrderLineItem::new);
    }

    @Provide
    Arbitrary<String> itemNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars(' ', '-')
            .ofMinLength(3)
            .ofMaxLength(50)
            .filter(name -> !name.isBlank());
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(new BigDecimal("0.01"), new BigDecimal("1000.00"))
            .ofScale(2);
    }

    @Provide
    Arbitrary<Money> moneys() {
        return prices().map(Money::new);
    }

    @Provide
    Arbitrary<DeliveryInfo> deliveryInfos() {
        Arbitrary<String> addresses = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars(' ', ',', '.', '-')
            .ofMinLength(10)
            .ofMaxLength(100)
            .filter(address -> !address.isBlank());
        Arbitrary<LocalDateTime> deliveryTimes = Arbitraries.longs()
            .between(1, 7 * 24)
            .map(hours -> LocalDateTime.now().plusHours(hours));
        return Combinators.combine(addresses, deliveryTimes).as(DeliveryInfo::new);
    }

    @Provide
    Arbitrary<PaymentInfo> paymentInfos() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(10)
            .ofMaxLength(50)
            .map(PaymentInfo::new);
    }
}
