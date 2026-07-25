package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.KafkaEventHeaders;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.domain.DeliveryStatus;
import net.ftgo.delivery.repository.DeliveryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    private ObjectMapper objectMapper;
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new OrderEventConsumer(
            deliveryRepository,
            processedMessageRepository,
            objectMapper
        );
    }

    @Test
    void createsDeliveryFromSharedOrderApprovedSnapshots() throws Exception {
        String eventId = "11111111-1111-1111-1111-111111111111";
        LocalDateTime deliveryTime = LocalDateTime.now().plusHours(1);
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        OrderApproved event = event(pickupAddress, deliveryAddress, deliveryTime);
        ConsumerRecord<String, String> record = orderRecord(
            "Order#101",
            objectMapper.writeValueAsString(event),
            "OrderApproved",
            eventId
        );

        when(processedMessageRepository.existsById(eventId)).thenReturn(false);
        when(deliveryRepository.findByOrderId(101L)).thenReturn(Optional.empty());

        consumer.handleOrderEvent(record);

        ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveryRepository).save(deliveryCaptor.capture());
        Delivery delivery = deliveryCaptor.getValue();
        assertEquals(101L, delivery.getOrderId());
        assertEquals(pickupAddress, delivery.getPickupAddress());
        assertEquals(deliveryAddress, delivery.getDeliveryAddress());
        assertEquals(deliveryTime, delivery.getScheduledTime());
        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }

    @Test
    void ignoresOrderEventsThatAreNotOrderApproved() {
        ConsumerRecord<String, String> record = orderRecord(
            "Order#101",
            "{}",
            "OrderCreated",
            "22222222-2222-2222-2222-222222222222"
        );

        consumer.handleOrderEvent(record);

        verify(deliveryRepository, never()).save(any(Delivery.class));
        verify(processedMessageRepository, never()).save(any(ProcessedMessage.class));
    }

    @Test
    void createsOneDeliveryPerApprovedOrderIdempotently() throws Exception {
        String firstEventId = "33333333-3333-3333-3333-333333333333";
        String secondEventId = "44444444-4444-4444-4444-444444444444";
        LocalDateTime deliveryTime = LocalDateTime.now().plusHours(1);
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        OrderApproved event = event(pickupAddress, deliveryAddress, deliveryTime);
        String payload = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> firstMessage = orderRecord("Order#101", payload, "OrderApproved", firstEventId);
        ConsumerRecord<String, String> retriedAsNewMessage = orderRecord("Order#101", payload, "OrderApproved", secondEventId);
        Delivery existingDelivery = new Delivery(101L, pickupAddress, deliveryAddress, deliveryTime);

        when(processedMessageRepository.existsById(firstEventId)).thenReturn(false);
        when(processedMessageRepository.existsById(secondEventId)).thenReturn(false);
        when(deliveryRepository.findByOrderId(101L)).thenReturn(Optional.empty(), Optional.of(existingDelivery));

        consumer.handleOrderEvent(firstMessage);
        consumer.handleOrderEvent(retriedAsNewMessage);

        verify(deliveryRepository).save(any(Delivery.class));
        verify(processedMessageRepository, times(2)).save(any(ProcessedMessage.class));
    }

    private OrderApproved event(
        Address pickupAddress,
        Address deliveryAddress,
        LocalDateTime deliveryTime
    ) {
        return new OrderApproved(
            101L,
            202L,
            303L,
            new Money("30.97"),
            404L,
            505L,
            pickupAddress,
            deliveryAddress,
            deliveryTime
        );
    }

    private ConsumerRecord<String, String> orderRecord(
        String key,
        String payload,
        String eventType,
        String eventId
    ) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "net.ftgo.orderservice.domain.Order",
            0,
            0L,
            key,
            payload
        );
        record.headers().add(KafkaEventHeaders.EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaEventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
