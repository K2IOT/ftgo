package net.ftgo.common.orderflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAuthorizationSerializationTest {

    @Test
    void paymentTokenRoundTripsAcrossTheWireContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AuthorizeCardCommand command = new AuthorizeCardCommand(
            301L,
            101L,
            new Money("25.00"),
            "tok_e2e_decline",
            "order-101-authorize"
        );

        AuthorizeCardCommand parsed = mapper.readValue(
            mapper.writeValueAsString(command),
            AuthorizeCardCommand.class
        );

        assertThat(parsed.getPaymentToken()).isEqualTo("tok_e2e_decline");
        assertThat(parsed.getOrderId()).isEqualTo(101L);
        assertThat(parsed.getRequestId()).isEqualTo("order-101-authorize");
    }
}
