package net.ftgo.delivery.config;

import net.ftgo.delivery.messaging.OrderEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeliveryPickupResolverContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void registersEventConsumerWithoutRestaurantClientBridge() {
        assertThat(context.getBeansOfType(OrderEventConsumer.class)).hasSize(1);
        assertThat(Arrays.asList(context.getBeanDefinitionNames()))
            .noneMatch(name -> name.toLowerCase().contains("restaurantrestclient"))
            .noneMatch(name -> name.toLowerCase().contains("pickupaddressresolver"));
    }
}
