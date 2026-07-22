package net.ftgo.delivery.config;

import net.ftgo.delivery.messaging.HttpRestaurantPickupAddressResolver;
import net.ftgo.delivery.messaging.OrderEventConsumer;
import net.ftgo.delivery.messaging.RestaurantPickupAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeliveryPickupResolverContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void registersOneResolverAndOrderEventConsumer() {
        assertThat(context.getBeansOfType(RestaurantPickupAddressResolver.class))
                .hasSize(1)
                .containsValue(context.getBean(HttpRestaurantPickupAddressResolver.class));
        assertThat(context.getBeansOfType(OrderEventConsumer.class)).hasSize(1);
    }
}
