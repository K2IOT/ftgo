package net.ftgo.common.orderflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.menu.OrderMenuLineItem;
import net.ftgo.common.orderflow.replies.OrderMenuValidated;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMenuValidatedPickupAddressContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void pickupAddressSurvivesCommandReplySerialization() throws Exception {
        Address pickup = new Address("10 Kitchen Road", "Bangkok", "Bangkok", "10110");
        OrderMenuValidated reply = new OrderMenuValidated(
            101L,
            42L,
            7L,
            pickup,
            List.of(new OrderMenuLineItem(11L, "Burger", new Money("12.50"), 2)),
            new Money("25.00")
        );

        String json = objectMapper.writeValueAsString(reply);
        OrderMenuValidated parsed = objectMapper.readValue(json, OrderMenuValidated.class);

        assertThat(parsed.getPickupAddress()).isEqualTo(pickup);
        assertThat(json).contains("pickupAddress");
    }

    @Test
    void oldReplyWithoutPickupAddressRemainsReadable() throws Exception {
        String previousPayload = """
            {
              "orderId":101,
              "restaurantId":42,
              "currentMenuVersion":7,
              "authoritativeLineItems":[],
              "authoritativeTotal":{"amount":25.00}
            }
            """;

        OrderMenuValidated parsed = objectMapper.readValue(previousPayload, OrderMenuValidated.class);

        assertThat(parsed.getPickupAddress()).isNull();
        assertThat(parsed.getCurrentMenuVersion()).isEqualTo(7L);
    }
}
