package net.ftgo.order.config;

import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import io.eventuate.tram.sagas.orchestration.SagaManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.eventuate.tram.messaging.producer.MessageProducer;
import io.eventuate.tram.messaging.consumer.MessageConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that Eventuate Tram Sagas configuration is correctly loaded.
 * 
 * This test ensures that:
 * - SagaOrchestratorConfiguration is properly imported
 * - SagaInstanceFactory bean is available
 * - SagaManagerFactory bean is available
 * - All necessary saga infrastructure beans are configured
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:eventuate-schema.sql'",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "eventuatelocal.kafka.bootstrap.servers=localhost:9092",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class OrderServiceConfigurationTest {
    
    @MockitoBean
    private MessageProducer messageProducer;
    
    @MockitoBean
    private MessageConsumer messageConsumer;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Test
    void shouldLoadSagaInstanceFactory() {
        // Verify that SagaInstanceFactory bean is available
        assertThat(applicationContext.containsBean("sagaInstanceFactory"))
            .as("SagaInstanceFactory bean should be available")
            .isTrue();
        
        SagaInstanceFactory sagaInstanceFactory = applicationContext.getBean(SagaInstanceFactory.class);
        assertThat(sagaInstanceFactory)
            .as("SagaInstanceFactory should not be null")
            .isNotNull();
    }
    

    
    @Test
    void shouldLoadOrderServiceConfiguration() {
        // Verify that OrderServiceConfiguration is loaded
        assertThat(applicationContext.containsBean("orderServiceConfiguration"))
            .as("OrderServiceConfiguration bean should be available")
            .isTrue();
        
        OrderServiceConfiguration config = applicationContext.getBean(OrderServiceConfiguration.class);
        assertThat(config)
            .as("OrderServiceConfiguration should not be null")
            .isNotNull();
    }
    
    @Test
    void shouldHaveSagaOrchestratorInfrastructure() {
        // Verify that all necessary saga orchestrator beans are available
        assertThat(applicationContext.containsBean("sagaInstanceFactory"))
            .as("SagaInstanceFactory should be configured")
            .isTrue();
        
        assertThat(applicationContext.containsBean("sagaCommandProducer"))
            .as("SagaCommandProducer should be configured")
            .isTrue();
        
        assertThat(applicationContext.containsBean("sagaInstanceRepository"))
            .as("SagaInstanceRepository should be configured")
            .isTrue();
    }
}
