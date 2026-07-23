package net.ftgo.order.saga;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared infrastructure for Order Service saga integration tests.
 *
 * <p>The containers are JVM singletons rather than JUnit-managed @Container
 * fields. Spring caches the application context across subclasses, while the
 * JUnit Testcontainers extension stops inherited static containers after each
 * test class. That combination leaves cached Eventuate consumers connected to
 * a broker that has already been stopped. Starting the containers once keeps
 * the cached context and its broker/database endpoints valid for the complete
 * module test run.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class OrderServiceIntegrationTestBase {

    static final MySQLContainer<?> mysql =
        new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("ftgo_order_test")
            .withUsername("test")
            .withPassword("test");

    static final KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static {
        mysql.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eventuatelocal.kafka.bootstrap.servers", kafka::getBootstrapServers);
        registry.add("eventuate.database.schema", () -> "none");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
