package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaActions;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.order.domain.OrderLineItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
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
        CommandWithDestination command = unwrapCommand(actions.getCommands().get(0));
        assertThat(command.getDestinationChannel())
            .isEqualTo(ChannelNames.RESTAURANT_SERVICE_COMMAND_CHANNEL);
        assertThat(command.getCommand()).isInstanceOf(ValidateOrderMenuCommand.class);
    }

    private CommandWithDestination unwrapCommand(Object wrapper) {
        return Arrays.stream(wrapper.getClass().getDeclaredFields())
            .filter(field -> CommandWithDestination.class.isAssignableFrom(field.getType()))
            .findFirst()
            .map(field -> readCommandField(field, wrapper))
            .orElseThrow(() -> new AssertionError(
                "Eventuate command wrapper does not contain CommandWithDestination: "
                    + wrapper.getClass().getName()
            ));
    }

    private CommandWithDestination readCommandField(Field field, Object wrapper) {
        try {
            field.setAccessible(true);
            return (CommandWithDestination) field.get(wrapper);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Unable to inspect Eventuate command wrapper", e);
        }
    }
}
