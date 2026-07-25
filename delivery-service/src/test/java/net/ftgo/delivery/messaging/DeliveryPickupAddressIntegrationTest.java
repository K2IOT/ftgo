package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.repository.DeliveryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeliveryPickupAddressIntegrationTest {

    @MockBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private OrderEventConsumer orderEventConsumer;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        processedMessageRepository.deleteAll();
        deliveryRepository.deleteAll();
    }

    @Test
    void persistsPickupAddressFromEventWithoutRestaurantNetworkDependency() throws Exception {
        long restaurantId = 42L;
        long orderId = 101L;
        Address pickupAddress =
            new Address("10 Kitchen Road", "Bangkok", "Bangkok", "10110");
        Address deliveryAddress =
            new Address("99 Consumer Avenue", "Bangkok", "Bangkok", "10260");
        LocalDateTime deliveryTime = LocalDateTime.now()
            .plusHours(1)
            .truncatedTo(ChronoUnit.MICROS);

        OrderApproved event = new OrderApproved(
            orderId,
            7L,
            restaurantId,
            new Money(new BigDecimal("25.50")),
            15L,
            19L,
            pickupAddress,
            deliveryAddress,
            deliveryTime
        );
        String payload = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "net.ftgo.orderservice.domain.Order",
            0,
            0L,
            "message-101",
            payload
        );
        record.headers().add(
            "eventType",
            "OrderApproved".getBytes(StandardCharsets.UTF_8)
        );

        orderEventConsumer.handleOrderEvent(record);

        Delivery persisted = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(persisted.getPickupAddress()).isEqualTo(pickupAddress);
        assertThat(persisted.getDeliveryAddress())
            .as("OrderApproved delivery address must survive JSON and persistence; payload=%s, persisted=%s",
                payload, persisted.getDeliveryAddress())
            .isEqualTo(deliveryAddress);
        assertThat(persisted.getScheduledTime()).isEqualTo(deliveryTime);
        assertThat(processedMessageRepository.existsById("message-101")).isTrue();
    }
}
