package net.ftgo.consumer.config;

import io.eventuate.tram.spring.jdbckafka.TramJdbcKafkaConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Connects Consumer Service messaging components to the Eventuate JDBC/Kafka transport.
 *
 * <p>The command dispatcher itself is owned by {@link ConsumerServiceConfiguration};
 * this configuration must not register a second dispatcher bean.</p>
 */
@Configuration
@Profile("!test")
@Import(TramJdbcKafkaConfiguration.class)
public class EventuateTramConfiguration {
}
