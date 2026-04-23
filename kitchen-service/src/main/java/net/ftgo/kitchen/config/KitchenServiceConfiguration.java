package net.ftgo.kitchen.config;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
import io.eventuate.tram.sagas.spring.participant.SagaParticipantConfiguration;
import io.eventuate.tram.spring.events.publisher.TramEventsPublisherConfiguration;
import net.ftgo.kitchen.messaging.KitchenServiceCommandHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for Kitchen Service.
 * 
 * Configures:
 * - Eventuate Tram for saga participation
 * - Command dispatcher for handling saga commands
 * - Event publisher for domain events
 */
@Configuration
@Import({
    SagaParticipantConfiguration.class,
    TramEventsPublisherConfiguration.class
})
public class KitchenServiceConfiguration {
    
    /**
     * Creates command dispatcher for Kitchen Service.
     * Dispatches saga commands to command handlers.
     * 
     * @param sagaCommandDispatcherFactory factory for creating command dispatcher
     * @param kitchenServiceCommandHandlers command handlers
     * @return configured command dispatcher
     */
    @Bean
    public CommandDispatcher commandDispatcher(
            SagaCommandDispatcherFactory sagaCommandDispatcherFactory,
            KitchenServiceCommandHandlers kitchenServiceCommandHandlers) {
        
        return sagaCommandDispatcherFactory.make(
            "kitchenServiceDispatcher",
            kitchenServiceCommandHandlers.commandHandlers()
        );
    }
}
