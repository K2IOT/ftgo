package net.ftgo.accounting.config;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
import io.eventuate.tram.sagas.spring.participant.SagaParticipantConfiguration;
import io.eventuate.tram.spring.events.publisher.TramEventsPublisherConfiguration;
import net.ftgo.accounting.messaging.AccountingServiceCommandHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for Accounting Service messaging infrastructure.
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
public class AccountingServiceConfiguration {
    
    /**
     * Creates the command dispatcher for Accounting Service.
     * 
     * @param accountingServiceCommandHandlers the command handlers
     * @param sagaCommandDispatcherFactory the saga command dispatcher factory
     * @return configured CommandDispatcher
     */
    @Bean
    public CommandDispatcher accountingCommandDispatcher(
            AccountingServiceCommandHandlers accountingServiceCommandHandlers,
            SagaCommandDispatcherFactory sagaCommandDispatcherFactory) {
        return sagaCommandDispatcherFactory.make(
            "accountingServiceDispatcher",
            accountingServiceCommandHandlers.commandHandlers()
        );
    }
}
