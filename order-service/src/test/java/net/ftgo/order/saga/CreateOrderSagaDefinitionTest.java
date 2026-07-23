package net.ftgo.order.saga;

import io.eventuate.tram.sagas.orchestration.SagaActions;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.order.domain.OrderLineItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderSagaDefinitionTest {

    @Test
    void startEmitsRestaurantMenuValidationAfterCompensationOnlyStep() {
        CreateOrderSagaData data = new CreateOrderSagaData(
            101L,
            301L,
            202L,
            List.of(new OrderLineItem(11L, "Burger", new Money("25.00"), 1)),
            new Money("25.00"),
            7L
        );

        SagaActions<CreateOrderSagaData> actions =
            new CreateOrderSaga().getSagaDefinition().start(data);

        assertThat(actions.getCommands()).hasSize(1);
        var command = actions.getCommands().get(0);
        assertThat(command.getDestinationChannel())
            .isEqualTo(ChannelNames.RESTAURANT_SERVICE_COMMAND_CHANNEL);
        assertThat(command.getCommand()).isInstanceOf(ValidateOrderMenuCommand.class);
    }
}
