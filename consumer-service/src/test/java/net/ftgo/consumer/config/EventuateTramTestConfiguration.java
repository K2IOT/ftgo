package net.ftgo.consumer.config;

import io.eventuate.tram.commands.producer.CommandProducer;
import io.eventuate.tram.events.publisher.DomainEventPublisher;
import io.eventuate.tram.messaging.producer.MessageProducer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Test configuration that provides mock beans for Eventuate Tram components.
 * This allows tests to run without requiring Kafka infrastructure.
 */
@TestConfiguration
public class EventuateTramTestConfiguration {
    
    @Bean
    @Primary
    public MessageProducer messageProducer() {
        return mock(MessageProducer.class);
    }
    
    @Bean
    @Primary
    public DomainEventPublisher domainEventPublisher() {
        return mock(DomainEventPublisher.class);
    }
    
    @Bean
    @Primary
    public CommandProducer commandProducer() {
        return mock(CommandProducer.class);
    }
}
