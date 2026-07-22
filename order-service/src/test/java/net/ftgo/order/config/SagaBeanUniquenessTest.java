package net.ftgo.order.config;

import net.ftgo.order.saga.CancelOrderSagaLocalSteps;
import net.ftgo.order.saga.CreateOrderSagaLocalSteps;
import net.ftgo.order.saga.OrderServiceIntegrationTestBase;
import net.ftgo.order.saga.ReviseOrderSagaLocalSteps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class SagaBeanUniquenessTest extends OrderServiceIntegrationTestBase {

    @Autowired
    private ApplicationContext context;

    @Test
    void registersExactlyOneLocalStepsBeanPerSaga() {
        assertThat(context.getBeansOfType(CreateOrderSagaLocalSteps.class)).hasSize(1);
        assertThat(context.getBeansOfType(CancelOrderSagaLocalSteps.class)).hasSize(1);
        assertThat(context.getBeansOfType(ReviseOrderSagaLocalSteps.class)).hasSize(1);
    }
}
