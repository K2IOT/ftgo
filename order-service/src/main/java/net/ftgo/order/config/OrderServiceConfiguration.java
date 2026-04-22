package net.ftgo.order.config;

import io.eventuate.tram.sagas.spring.orchestration.SagaOrchestratorConfiguration;
import io.eventuate.tram.spring.events.publisher.TramEventsPublisherConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for Order Service messaging and saga orchestration infrastructure.
 * 
 * The Order Service acts as the saga orchestrator for:
 * - CreateOrderSaga: Coordinates order creation across Consumer, Kitchen, and Accounting services
 * - CancelOrderSaga: Coordinates order cancellation
 * - ReviseOrderSaga: Coordinates order revision
 * 
 * This configuration:
 * - Enables Eventuate Tram Saga orchestration via SagaOrchestratorConfiguration
 * - Configures saga instance repository with MySQL (uses saga_instance tables from migration)
 * - Sets up saga command channels for all participant services
 * - Configures saga reply channels (createOrderSagaReply, cancelOrderSagaReply, reviseOrderSagaReply)
 * - Enables event publishing for domain events
 * 
 * The SagaOrchestratorConfiguration automatically provides:
 * - SagaInstanceFactory: Creates and manages saga instances
 * - SagaManagerFactory: Creates saga managers for each saga type
 * - SagaCommandProducer: Sends commands to participant services
 * - SagaInstanceRepository: Persists saga state to MySQL
 */
@Configuration
@Import({
    SagaOrchestratorConfiguration.class,
    TramEventsPublisherConfiguration.class
})
public class OrderServiceConfiguration {
    
    // SagaOrchestratorConfiguration automatically configures:
    // - SagaInstanceFactory bean
    // - SagaManagerFactory bean
    // - SagaCommandProducer bean
    // - SagaInstanceRepository bean (backed by MySQL saga_instance tables)
    // - Command channels for participant services (consumerService, kitchenService, accountingService, deliveryService)
    // - Reply channels for sagas (createOrderSagaReply, cancelOrderSagaReply, reviseOrderSagaReply)
}

