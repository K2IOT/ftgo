package net.ftgo.order.config;

import io.eventuate.tram.spring.events.publisher.TramEventsPublisherConfiguration;
import io.eventuate.tram.spring.commands.producer.TramCommandProducerConfiguration;
import io.eventuate.tram.spring.jdbckafka.TramJdbcKafkaConfiguration;
import io.eventuate.messaging.kafka.basic.consumer.DefaultKafkaConsumerFactory;
import io.eventuate.messaging.kafka.basic.consumer.KafkaConsumerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for Eventuate Tram messaging infrastructure.
 * 
 * Imports:
 * - TramEventsPublisherConfiguration: Enables domain event publishing
 *   - Events are inserted into outbox table within same transaction as business data
 *   - Debezium CDC monitors outbox table and publishes to Kafka
 * 
 * - TramCommandProducerConfiguration: Enables saga command sending
 *   - Sends commands to saga participants via Kafka command channels
 *   - Handles command replies from participants
 * 
 * Database Configuration:
 * - Uses MySQL datasource configured in application.yml
 * - Saga state persisted in saga_instance table
 * - Outbox messages stored in outbox table
 * - Processed messages tracked in processed_messages table
 * 
 * Kafka Configuration:
 * - Bootstrap servers configured via eventuatelocal.kafka.bootstrap.servers
 * - Command channels: consumerService, kitchenService, accountingService, deliveryService
 * - Reply channels: createOrderSagaReply, cancelOrderSagaReply, reviseOrderSagaReply
 * - Event topics: net.ftgo.orderservice.domain.Order, etc.
 */
@Configuration
@Import({
    TramEventsPublisherConfiguration.class,
    TramCommandProducerConfiguration.class,
    TramJdbcKafkaConfiguration.class
})
public class EventuateTramConfiguration {
    // Provide a dedicated Eventuate KafkaConsumerFactory bean to avoid name/type
    // collisions with Spring Kafka's own kafkaConsumerFactory bean.
    @Bean
    public KafkaConsumerFactory eventuateKafkaConsumerFactory() {
        return new DefaultKafkaConsumerFactory();
    }
}
