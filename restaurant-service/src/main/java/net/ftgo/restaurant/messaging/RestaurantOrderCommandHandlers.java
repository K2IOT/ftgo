package net.ftgo.restaurant.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.ValidateOrderMenuCommand;
import net.ftgo.common.orderflow.replies.OrderMenuValidationRejected;
import net.ftgo.restaurant.service.OrderMenuValidationException;
import net.ftgo.restaurant.service.OrderMenuValidationService;
import org.springframework.stereotype.Component;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

@Component
public class RestaurantOrderCommandHandlers {

    private final OrderMenuValidationService validationService;

    public RestaurantOrderCommandHandlers(OrderMenuValidationService validationService) {
        this.validationService = validationService;
    }

    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.RESTAURANT_SERVICE_COMMAND_CHANNEL)
            .onMessage(ValidateOrderMenuCommand.class, this::validateOrderMenu)
            .build();
    }

    Message validateOrderMenu(CommandMessage<ValidateOrderMenuCommand> message) {
        ValidateOrderMenuCommand command = message.getCommand();
        try {
            return withSuccess(validationService.validate(command));
        } catch (OrderMenuValidationException e) {
            return withSuccess(new OrderMenuValidationRejected(
                command.getOrderId(), e.getReasonCode(), e.getMessage()));
        }
    }
}
