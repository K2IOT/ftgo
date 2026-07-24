package net.ftgo.restaurant.config;

import io.eventuate.tram.spring.jdbckafka.TramJdbcKafkaConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@Import(TramJdbcKafkaConfiguration.class)
public class EventuateTramConfiguration {
}
