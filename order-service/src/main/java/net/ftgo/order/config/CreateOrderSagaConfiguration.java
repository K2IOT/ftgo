package net.ftgo.order.config;

import io.eventuate.tram.sagas.spring.participant.SagaParticipantConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.CreateOrderSaga;
import net.ftgo.order.saga.CreateOrderSagaLocalSteps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for CreateOrderSaga.
 * 
 * Registers the saga definition and local saga participant as Spring beans.
 * The saga orchestrator will use these beans to execute the saga.
 */
@Configuration
@Import(SagaParticipantConfiguration.class)
public class CreateOrderSagaConfiguration {
    
    /**
     * Creates the CreateOrderSaga bean.
     * 
     * @return the CreateOrderSaga instance
     */
    @Bean
    public CreateOrderSaga createOrderSaga() {
        return new CreateOrderSaga();
    }
    
    /**
     * Creates the local saga participant for CreateOrderSaga.
     * 
     * @param orderRepository the order repository
     * @param eventPublisher the domain event publisher
     * @param meterRegistry the metrics registry
     * @return the CreateOrderSagaLocalSteps instance
     */
    @Bean
    public CreateOrderSagaLocalSteps createOrderSagaLocalSteps(
            OrderRepository orderRepository,
            DomainEventPublisher eventPublisher,
            MeterRegistry meterRegistry) {
        return new CreateOrderSagaLocalSteps(orderRepository, eventPublisher, meterRegistry);
    }
}
