package net.ftgo.common.orderflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderApprovedContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void currentContractRoundTripsPickupAndDeliverySnapshots() throws Exception {
        Address pickup = new Address("10 Kitchen Road", "Bangkok", "Bangkok", "10110");
        Address delivery = new Address("99 Consumer Avenue", "Bangkok", "Bangkok", "10260");
        LocalDateTime deliveryTime = LocalDateTime.of(2026, 7, 24, 18, 30);

        OrderApproved event = new OrderApproved(
            101L,
            7L,
            42L,
            new Money("25.50"),
            15L,
            19L,
            pickup,
            delivery,
            deliveryTime
        );

        String json = objectMapper.writeValueAsString(event);
        OrderApproved parsed = objectMapper.readValue(json, OrderApproved.class);

        assertThat(parsed.getPickupAddress()).isEqualTo(pickup);
        assertThat(parsed.getDeliveryAddress()).isEqualTo(delivery);
        assertThat(parsed.getDeliveryTime()).isEqualTo(deliveryTime);
        assertThat(json).contains("pickupAddress", "deliveryAddress", "deliveryTime");
    }

    @Test
    void previousContractWithoutPickupAddressRemainsReadableDuringCompatibilityWindow() throws Exception {
        String previousPayload = """
            {
              "orderId":101,
              "consumerId":7,
              "restaurantId":42,
              "orderTotal":{"amount":25.50},
              "ticketId":15,
              "authorizationId":19,
              "deliveryAddress":{
                "street":"99 Consumer Avenue",
                "city":"Bangkok",
                "state":"Bangkok",
                "zipCode":"10260"
              },
              "deliveryTime":"2026-07-24T18:30:00"
            }
            """;

        OrderApproved parsed = objectMapper.readValue(previousPayload, OrderApproved.class);

        assertThat(parsed.getPickupAddress()).isNull();
        assertThat(parsed.getDeliveryAddress())
            .isEqualTo(new Address("99 Consumer Avenue", "Bangkok", "Bangkok", "10260"));
    }
}
