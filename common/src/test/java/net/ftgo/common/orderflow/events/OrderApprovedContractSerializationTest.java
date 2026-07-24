package net.ftgo.common.orderflow.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderApprovedContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesAndDeserializesCanonicalOrderApprovedWithAddressSnapshots() throws Exception {
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        LocalDateTime deliveryTime = LocalDateTime.of(2026, 5, 18, 10, 30);
        OrderApproved event = new OrderApproved(
            101L,
            202L,
            303L,
            new Money("30.97"),
            404L,
            505L,
            pickupAddress,
            deliveryAddress,
            deliveryTime
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertNotNull(node.get("pickupAddress"));
        assertNotNull(node.get("deliveryAddress"));
        assertNotNull(node.get("deliveryTime"));
        assertFalse(node.has("pickupLocation"));
        assertFalse(node.has("restaurantPickupAddress"));

        OrderApproved roundTrip = objectMapper.readValue(json, OrderApproved.class);
        assertEquals(101L, roundTrip.getOrderId());
        assertEquals(202L, roundTrip.getConsumerId());
        assertEquals(303L, roundTrip.getRestaurantId());
        assertEquals(new Money("30.97"), roundTrip.getOrderTotal());
        assertEquals(404L, roundTrip.getTicketId());
        assertEquals(505L, roundTrip.getAuthorizationId());
        assertEquals(pickupAddress, roundTrip.getPickupAddress());
        assertEquals(deliveryAddress, roundTrip.getDeliveryAddress());
        assertEquals(deliveryTime, roundTrip.getDeliveryTime());
    }

    @Test
    void supportsBackwardCompatibilityWhenLegacyPayloadOmitsAddressFields() throws Exception {
        String legacyJson = """
            {
              "orderId": 101,
              "consumerId": 202,
              "restaurantId": 303,
              "orderTotal": {"amount": 30.97},
              "ticketId": 404,
              "authorizationId": 505
            }
            """;

        OrderApproved parsed = objectMapper.readValue(legacyJson, OrderApproved.class);

        assertEquals(101L, parsed.getOrderId());
        assertNull(parsed.getPickupAddress());
        assertNull(parsed.getDeliveryAddress());
        assertNull(parsed.getDeliveryTime());
    }
}
