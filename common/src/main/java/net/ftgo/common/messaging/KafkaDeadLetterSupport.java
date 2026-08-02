package net.ftgo.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Shared bounded retry, DLT routing, metadata and metrics for event consumers. */
public final class KafkaDeadLetterSupport {

    public static final String DLT_SUFFIX = ".DLT";
    public static final String DLT_EVENT_ID = "ftgo_dlt_event_id";
    public static final String DLT_CORRELATION_ID = "ftgo_dlt_correlation_id";
    public static final String DLT_EXCEPTION_CLASS = "ftgo_dlt_exception_class";

    private static final long[] RETRY_INTERVALS_MS = {1_000L, 5_000L, 30_000L};

    private KafkaDeadLetterSupport() {
    }

    public static DefaultErrorHandler errorHandler(
        KafkaTemplate<Object, Object> kafkaTemplate,
        MeterRegistry meterRegistry
    ) {
        return errorHandler(kafkaTemplate, meterRegistry, retryBackOff());
    }

    public static DefaultErrorHandler errorHandler(
        KafkaTemplate<Object, Object> kafkaTemplate,
        MeterRegistry meterRegistry,
        BackOff backOff
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> deadLetterDestination(record)
        );
        recoverer.setVerifyPartition(false);
        recoverer.setHeadersFunction((record, exception) ->
            deadLetterHeaders(record, exception, meterRegistry));

        DefaultErrorHandler handler = new DefaultErrorHandler(
            recoverer,
            Objects.requireNonNull(backOff, "backOff")
        );
        handler.addNotRetryableExceptions(
            NonRetryableEventException.class,
            IllegalArgumentException.class,
            JsonProcessingException.class
        );
        handler.addRetryableExceptions(RetryableEventException.class);
        return handler;
    }

    public static BackOff retryBackOff() {
        return retryBackOff(RETRY_INTERVALS_MS);
    }

    public static BackOff retryBackOff(long... retryIntervalsMs) {
        Objects.requireNonNull(retryIntervalsMs, "retryIntervalsMs");
        if (retryIntervalsMs.length == 0) {
            throw new IllegalArgumentException("At least one retry interval is required");
        }
        long[] intervals = Arrays.copyOf(retryIntervalsMs, retryIntervalsMs.length);
        for (long interval : intervals) {
            if (interval < 0) {
                throw new IllegalArgumentException("Retry intervals cannot be negative");
            }
        }
        return () -> new BackOffExecution() {
            private int index;

            @Override
            public long nextBackOff() {
                if (index >= intervals.length) {
                    return STOP;
                }
                return intervals[index++];
            }
        };
    }

    public static TopicPartition deadLetterDestination(ConsumerRecord<?, ?> record) {
        Objects.requireNonNull(record, "record");
        return new TopicPartition(record.topic() + DLT_SUFFIX, record.partition());
    }

    public static Headers deadLetterHeaders(
        ConsumerRecord<?, ?> record,
        Exception exception,
        MeterRegistry meterRegistry
    ) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(exception, "exception");
        Objects.requireNonNull(meterRegistry, "meterRegistry");

        RecordHeaders headers = new RecordHeaders();
        copyHeader(record.headers(), headers, KafkaEventHeaders.EVENT_ID, DLT_EVENT_ID);
        copyHeader(record.headers(), headers, KafkaEventHeaders.CORRELATION_ID, DLT_CORRELATION_ID);
        headers.add(
            DLT_EXCEPTION_CLASS,
            exception.getClass().getName().getBytes(StandardCharsets.UTF_8)
        );

        meterRegistry.counter(
            "ftgo_kafka_dlt_total",
            "source_topic", record.topic(),
            "exception", exception.getClass().getName()
        ).increment();
        return headers;
    }

    public static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NonRetryableEventException
                || current instanceof IllegalArgumentException
                || current instanceof JsonProcessingException) {
                return false;
            }
            if (current instanceof RetryableEventException) {
                return true;
            }
            current = current.getCause();
        }
        return true;
    }

    public static String headerText(Headers headers, String name) {
        if (headers == null) {
            return null;
        }
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void copyHeader(
        Headers source,
        Headers target,
        String sourceName,
        String targetName
    ) {
        Header header = source == null ? null : source.lastHeader(sourceName);
        if (header != null && header.value() != null) {
            target.add(targetName, header.value().clone());
        }
    }
}
