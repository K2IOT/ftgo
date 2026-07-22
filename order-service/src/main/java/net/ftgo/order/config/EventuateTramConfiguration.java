package net.ftgo.order.config;

import io.eventuate.tram.spring.events.publisher.TramEventsPublisherConfiguration;
import io.eventuate.tram.spring.commands.producer.TramCommandProducerConfiguration;
import io.eventuate.tram.spring.jdbckafka.TramJdbcKafkaConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for Eventuate Tram messaging infrastructure.
 *
 * Eventuate owns the command/reply Kafka producer and consumer factories. The
 * application must not register an additional KafkaConsumerFactory because the
 * imported JDBC/Kafka configuration already provides the required bean.
 */
@Configuration
@Import({
    TramEventsPublisherConfiguration.class,
    TramCommandProducerConfiguration.class,
    TramJdbcKafkaConfiguration.class
})
public class EventuateTramConfiguration {
}
