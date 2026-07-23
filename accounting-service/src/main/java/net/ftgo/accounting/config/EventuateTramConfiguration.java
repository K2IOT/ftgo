package net.ftgo.accounting.config;

import io.eventuate.tram.spring.jdbckafka.TramJdbcKafkaConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Connects Eventuate Tram command handlers to the JDBC/Kafka transport.
 */
@Configuration
@Import(TramJdbcKafkaConfiguration.class)
public class EventuateTramConfiguration {
}
