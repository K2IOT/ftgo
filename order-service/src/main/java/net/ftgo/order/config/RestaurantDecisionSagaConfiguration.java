package net.ftgo.order.config;

import net.ftgo.order.saga.ConfirmOrderSaga;
import net.ftgo.order.saga.ConfirmOrderSagaLocalSteps;
import net.ftgo.order.saga.RejectOrderSaga;
import net.ftgo.order.saga.RejectOrderSagaLocalSteps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Saga definitions started by kitchen decision events.
 */
@Configuration
public class RestaurantDecisionSagaConfiguration {

    @Bean
    public ConfirmOrderSaga confirmOrderSaga(ConfirmOrderSagaLocalSteps localSteps) {
        return new ConfirmOrderSaga(localSteps);
    }

    @Bean
    public RejectOrderSaga rejectOrderSaga(RejectOrderSagaLocalSteps localSteps) {
        return new RejectOrderSaga(localSteps);
    }
}
