package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
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

    @Mock
    private RestaurantPickupAddressResolver pickupAddressResolver;

    private ObjectMapper objectMapper;
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new OrderEventConsumer(
            deliveryRepository,
            processedMessageRepository,
            pickupAddressResolver,
            objectMapper
        );
    }

    @Test
    void createsDeliveryFromSharedOrderApproved() throws Exception {
        LocalDateTime deliveryTime = LocalDateTime.now().plusHours(1);
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        OrderApproved event = new OrderApproved(
            101L,
            202L,
            303L,
            new Money("30.97"),
            404L,
            505L,
            deliveryAddress,
            deliveryTime
        );
        ConsumerRecord<String, String> record = orderRecord(
            "message-1",
            objectMapper.writeValueAsString(event),
            "OrderApproved"
        );

        when(processedMessageRepository.existsById("message-1")).thenReturn(false);
        when(deliveryRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(pickupAddressResolver.resolvePickupAddress(303L)).thenReturn(pickupAddress);

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
    void ignoresOrderEventsThatAreNotOrderApproved() throws Exception {
        ConsumerRecord<String, String> record = orderRecord(
            "message-2",
            "{}",
            "OrderCreated"
        );

        consumer.handleOrderEvent(record);

        verify(deliveryRepository, never()).save(any(Delivery.class));
        verify(processedMessageRepository, never()).save(any(ProcessedMessage.class));
        verify(pickupAddressResolver, never()).resolvePickupAddress(any());
    }

    @Test
    void createsOneDeliveryPerApprovedOrderIdempotently() throws Exception {
        LocalDateTime deliveryTime = LocalDateTime.now().plusHours(1);
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        OrderApproved event = new OrderApproved(
            101L,
            202L,
            303L,
            new Money("30.97"),
            404L,
            505L,
            deliveryAddress,
            deliveryTime
        );
        String payload = objectMapper.writeValueAsString(event);
        ConsumerRecord<String, String> firstMessage = orderRecord("message-3", payload, "OrderApproved");
        ConsumerRecord<String, String> retriedAsNewMessage = orderRecord("message-4", payload, "OrderApproved");
        Delivery existingDelivery = new Delivery(101L, pickupAddress, deliveryAddress, deliveryTime);

        when(processedMessageRepository.existsById("message-3")).thenReturn(false);
        when(processedMessageRepository.existsById("message-4")).thenReturn(false);
        when(deliveryRepository.findByOrderId(101L)).thenReturn(Optional.empty(), Optional.of(existingDelivery));
        when(pickupAddressResolver.resolvePickupAddress(303L)).thenReturn(pickupAddress);

        consumer.handleOrderEvent(firstMessage);
        consumer.handleOrderEvent(retriedAsNewMessage);

        verify(deliveryRepository).save(any(Delivery.class));
        verify(processedMessageRepository, times(2)).save(any(ProcessedMessage.class));
    }

    private ConsumerRecord<String, String> orderRecord(String key, String payload, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "net.ftgo.orderservice.domain.Order",
            0,
            0L,
            key,
            payload
        );
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
