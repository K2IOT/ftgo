package net.ftgo.common.orderflow;

import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAuthorizationContractTest {

    @Test
    void authorizationCommandCarriesPaymentTokenToTheAccountingBoundary() {
        assertThat(Arrays.stream(AuthorizeCardCommand.class.getDeclaredFields())
            .map(Field::getName))
            .contains("paymentToken");
    }
}
