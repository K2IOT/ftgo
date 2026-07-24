package net.ftgo.order.saga;

import io.eventuate.common.json.mapper.JSonMapper;
import net.ftgo.common.Money;
import net.ftgo.order.domain.OrderLineItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderSagaPaymentTokenTest {

    @Test
    void paymentTokenSurvivesSagaPersistenceRoundTrip() {
        CreateOrderSagaData data = new CreateOrderSagaData(
            101L,
            301L,
            202L,
            List.of(new OrderLineItem(11L, "Burger", new Money("25.00"), 1)),
            new Money("25.00"),
            7L,
            "tok_e2e_decline"
        );

        CreateOrderSagaData parsed = JSonMapper.fromJson(
            JSonMapper.toJson(data),
            CreateOrderSagaData.class
        );

        assertThat(parsed.getPaymentToken()).isEqualTo("tok_e2e_decline");
        assertThat(parsed.getExpectedMenuVersion()).isEqualTo(7L);
    }
}
