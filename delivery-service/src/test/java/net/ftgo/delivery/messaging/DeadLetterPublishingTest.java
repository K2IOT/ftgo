package net.ftgo.delivery.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.common.messaging.KafkaDeadLetterSupport;
import net.ftgo.common.messaging.KafkaEventHeaders;
import net.ftgo.common.messaging.NonRetryableEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeadLetterPublishingTest {

    @Test
    void poisonEventHasBoundedRetryAndDltMetadataWithoutPayloadLabels() {
        BackOffExecution execution = KafkaDeadLetterSupport.retryBackOff().start();
        assertEquals(1_000L, execution.nextBackOff());
        assertEquals(5_000L, execution.nextBackOff());
        assertEquals(30_000L, execution.nextBackOff());
        assertEquals(BackOffExecution.STOP, execution.nextBackOff());

        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "net.ftgo.orderservice.domain.Order",
            2,
            99L,
            "123",
            "{malformed"
        );
        record.headers().add(
            KafkaEventHeaders.EVENT_ID,
            eventId.toString().getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(
            KafkaEventHeaders.CORRELATION_ID,
            "correlation-123".getBytes(StandardCharsets.UTF_8)
        );

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var headers = KafkaDeadLetterSupport.deadLetterHeaders(
            record,
            new NonRetryableEventException("invalid JSON"),
            registry
        );

        assertEquals(
            "net.ftgo.orderservice.domain.Order.DLT",
            KafkaDeadLetterSupport.deadLetterDestination(record).topic()
        );
        assertEquals(eventId.toString(), KafkaDeadLetterSupport.headerText(headers, "ftgo_dlt_event_id"));
        assertEquals("correlation-123", KafkaDeadLetterSupport.headerText(headers, "ftgo_dlt_correlation_id"));
        assertEquals(
            NonRetryableEventException.class.getName(),
            KafkaDeadLetterSupport.headerText(headers, "ftgo_dlt_exception_class")
        );
        assertFalse(KafkaDeadLetterSupport.isRetryable(new NonRetryableEventException("bad")));
        assertEquals(
            1.0,
            registry.get("ftgo_kafka_dlt_total")
                .tag("source_topic", "net.ftgo.orderservice.domain.Order")
                .tag("exception", NonRetryableEventException.class.getName())
                .counter()
                .count()
        );
    }
}
