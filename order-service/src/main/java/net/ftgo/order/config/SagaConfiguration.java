package net.ftgo.order.config;

import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import io.eventuate.tram.sagas.spring.orchestration.SagaOrchestratorConfiguration;
import io.eventuate.tram.spring.commands.common.TramCommandsCommonAutoConfiguration;
import io.eventuate.tram.spring.events.common.TramEventsCommonAutoConfiguration;
import io.eventuate.tram.spring.messaging.common.TramMessagingCommonAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for Eventuate Tram Sagas framework in Order Service.
 * 
 * Order Service acts as the saga orchestrator for:
 * - CreateOrderSaga: Coordinates order placement across Consumer, Kitchen, and Accounting services
 * - CancelOrderSaga: Coordinates order cancellation with Kitchen and Accounting services
 * - ReviseOrderSaga: Coordinates order revision with Kitchen and Accounting services
 * 
 * Saga State Persistence:
 * - Saga instances are stored in MySQL (saga_instance table)
 * - Saga locks are managed via saga_lock_table
 * - Saga participants tracked in saga_instance_participants table
 * 
 * Communication:
 * - Saga commands sent via Kafka command channels (consumerService, kitchenService, accountingService)
 * - Saga replies received via Kafka reply channels (createOrderSagaReply, cancelOrderSagaReply, reviseOrderSagaReply)
 * - Domain events published via Kafka event topics (net.ftgo.orderservice.domain.Order)
 * 
 * The SagaOrchestratorConfiguration import provides:
 * - SagaInstanceFactory bean for creating saga instances
 * - SagaManagerFactory for managing saga lifecycle
 * - Saga command producers and consumers
 * - Saga instance repository for MySQL persistence
 */
@Configuration
@Import({
    SagaOrchestratorConfiguration.class,
    TramCommandsCommonAutoConfiguration.class,
    TramEventsCommonAutoConfiguration.class,
    TramMessagingCommonAutoConfiguration.class
})
public class SagaConfiguration {
    // SagaInstanceFactory and other saga beans are provided by SagaOrchestratorConfiguration
    // Individual saga definitions will be registered as beans in their respective configuration classes
}
