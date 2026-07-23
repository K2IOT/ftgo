package net.ftgo.restaurant.config;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
import io.eventuate.tram.sagas.spring.participant.SagaParticipantConfiguration;
import io.eventuate.tram.spring.events.publisher.TramEventsPublisherConfiguration;
import net.ftgo.restaurant.messaging.RestaurantOrderCommandHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@Import({SagaParticipantConfiguration.class, TramEventsPublisherConfiguration.class})
public class RestaurantServiceConfiguration {

    @Bean
    public CommandDispatcher restaurantCommandDispatcher(
        RestaurantOrderCommandHandlers handlers,
        SagaCommandDispatcherFactory factory) {
        return factory.make("restaurantServiceDispatcher", handlers.commandHandlers());
    }
}
