package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.repository.DeliveryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeliveryPickupAddressIntegrationTest {

    private static final WireMockServer RESTAURANT_SERVICE =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        RESTAURANT_SERVICE.start();
    }

    @DynamicPropertySource
    static void restaurantServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("services.restaurant-service.url", RESTAURANT_SERVICE::baseUrl);
        registry.add("services.restaurant-service.max-retries", () -> 2);
    }

    @Autowired
    private OrderEventConsumer orderEventConsumer;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        RESTAURANT_SERVICE.resetAll();
        processedMessageRepository.deleteAll();
        deliveryRepository.deleteAll();
    }

    @AfterAll
    static void stopRestaurantService() {
        RESTAURANT_SERVICE.stop();
    }

    @Test
    void resolvesAndPersistsAuthoritativePickupAddressWhenOrderIsApproved() throws Exception {
        long restaurantId = 42L;
        long orderId = 101L;
        Address authoritativePickupAddress =
                new Address("10 Kitchen Road", "Bangkok", "Bangkok", "10110");
        Address deliveryAddress =
                new Address("99 Consumer Avenue", "Bangkok", "Bangkok", "10260");
        LocalDateTime deliveryTime = LocalDateTime.now().plusHours(1);

        RESTAURANT_SERVICE.stubFor(get(urlEqualTo(
                        "/internal/restaurants/42/pickup-address"))
                .willReturn(okJson("""
                        {
                          "restaurantId": 42,
                          "address": {
                            "street": "10 Kitchen Road",
                            "city": "Bangkok",
                            "state": "Bangkok",
                            "zipCode": "10110"
                          }
                        }
                        """)));

        OrderApproved event = new OrderApproved(
                orderId,
                7L,
                restaurantId,
                new Money(new BigDecimal("25.50")),
                15L,
                19L,
                deliveryAddress,
                deliveryTime);
        String payload = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "net.ftgo.orderservice.domain.Order",
                0,
                0L,
                "message-101",
                payload);
        record.headers().add(
                "eventType",
                "OrderApproved".getBytes(StandardCharsets.UTF_8));

        orderEventConsumer.handleOrderEvent(record);

        Delivery persisted = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(persisted.getPickupAddress()).isEqualTo(authoritativePickupAddress);
        assertThat(persisted.getDeliveryAddress())
                .as("OrderApproved delivery address must survive JSON and persistence; payload=%s, persisted=%s",
                        payload, persisted.getDeliveryAddress())
                .isEqualTo(deliveryAddress);
        assertThat(persisted.getScheduledTime()).isEqualTo(deliveryTime);
        assertThat(processedMessageRepository.existsById("message-101")).isTrue();
        assertThat(applicationContext.getBeansOfType(RestaurantPickupAddressResolver.class))
                .hasSize(1);
        RESTAURANT_SERVICE.verify(1, getRequestedFor(urlEqualTo(
                "/internal/restaurants/42/pickup-address")));
    }
}
