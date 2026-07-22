package net.ftgo.consumer.config;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.commands.consumer.CommandDispatcherFactory;
import io.eventuate.tram.spring.jdbckafka.TramJdbcKafkaConfiguration;
import net.ftgo.consumer.messaging.ConsumerCommandHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Eventuate Tram configuration for the consumer service.
 */
@Configuration
@Profile("!test")
@Import(TramJdbcKafkaConfiguration.class)
public class EventuateTramConfiguration {

    @Bean
    public CommandDispatcher consumerCommandDispatcher(
            CommandDispatcherFactory commandDispatcherFactory,
            ConsumerCommandHandlers consumerCommandHandlers) {
        return commandDispatcherFactory.make(
            "consumerCommandDispatcher",
            consumerCommandHandlers.commandHandlers()
        );
    }
}
