package net.ftgo.consumer.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.orderflow.commands.VerifyConsumerCommand;
import net.ftgo.common.orderflow.replies.ConsumerVerified;
import net.ftgo.consumer.service.ConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * Command handlers for Consumer Service saga participation.
 * 
 * Handles commands from CreateOrderSaga:
 * - VerifyConsumerCommand: Validates consumer exists and has sufficient credit
 */
@Component
public class ConsumerCommandHandlers {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsumerCommandHandlers.class);
    
    private final ConsumerService consumerService;
    
    public ConsumerCommandHandlers(ConsumerService consumerService) {
        this.consumerService = consumerService;
    }
    
    /**
     * Builds command handlers for Consumer Service.
     * 
     * @return CommandHandlers configured for consumerService channel
     */
    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
                .fromChannel("consumerService")
                .onMessage(VerifyConsumerCommand.class, this::handleVerifyConsumer)
                .build();
    }
    
    /**
     * Handles VerifyConsumerCommand from CreateOrderSaga.
     * 
     * Validates:
     * 1. Consumer exists
     * 2. Order total does not exceed available credit limit
     * 
     * @param cm the command message
     * @return success reply with ConsumerVerified or failure reply with error message
     */
    private Message handleVerifyConsumer(CommandMessage<VerifyConsumerCommand> cm) {
        VerifyConsumerCommand command = cm.getCommand();
        
        logger.info("Verifying consumer {} for order total {}", 
            command.getConsumerId(), command.getOrderTotal());
        
        try {
            boolean verified = consumerService.verifyConsumerCredit(
                command.getConsumerId(), 
                command.getOrderTotal()
            );
            
            if (verified) {
                logger.info("Consumer {} verified successfully", command.getConsumerId());
                return withSuccess(new ConsumerVerified(command.getConsumerId()));
            } else {
                logger.warn("Consumer {} verification failed: insufficient credit", 
                    command.getConsumerId());
                return withFailure("Insufficient credit limit");
            }
        } catch (IllegalArgumentException e) {
            logger.error("Consumer {} verification failed: {}", 
                command.getConsumerId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error verifying consumer {}", 
                command.getConsumerId(), e);
            return withFailure("Internal error verifying consumer");
        }
    }
}
