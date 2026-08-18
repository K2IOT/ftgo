package net.ftgo.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class KafkaProbe implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final String bootstrapServers;
    private final KafkaProducer<String, String> producer;

    public KafkaProbe(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30_000);
        producer = new KafkaProducer<>(properties);
    }

    public RecordMetadata duplicate(ConsumerRecord<String, String> source) {
        RecordHeaders headers = copyHeaders(source.headers());
        ProducerRecord<String, String> duplicate = new ProducerRecord<>(
            source.topic(),
            source.partition(),
            source.timestamp(),
            source.key(),
            source.value(),
            headers
        );
        return send(duplicate);
    }

    public RecordMetadata send(
        String topic,
        Integer partition,
        String key,
        String value,
        Map<String, String> headers
    ) {
        RecordHeaders recordHeaders = new RecordHeaders();
        headers.forEach((name, text) -> recordHeaders.add(
            name,
            text.getBytes(StandardCharsets.UTF_8)
        ));
        return send(new ProducerRecord<>(
            topic,
            partition,
            null,
            key,
            value,
            recordHeaders
        ));
    }

    public ConsumerRecord<String, String> awaitRecord(
        String topic,
        Predicate<ConsumerRecord<String, String>> predicate,
        Duration timeout
    ) {
        List<ConsumerRecord<String, String>> records = awaitRecords(
            topic,
            predicate,
            1,
            timeout
        );
        if (records.isEmpty()) {
            throw new AssertionError("No matching Kafka record received from " + topic);
        }
        return records.get(records.size() - 1);
    }

    public List<ConsumerRecord<String, String>> awaitRecords(
        String topic,
        Predicate<ConsumerRecord<String, String>> predicate,
        int minimumCount,
        Duration timeout
    ) {
        Instant deadline = Instant.now().plus(timeout);
        List<ConsumerRecord<String, String>> matches = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(topic));
            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : polled) {
                    if (predicate.test(record)) {
                        matches.add(record);
                    }
                }
                if (matches.size() >= minimumCount) {
                    return matches;
                }
            }
        }
        return matches;
    }

    public int countRecords(
        String topic,
        Predicate<ConsumerRecord<String, String>> predicate,
        Duration collectionWindow
    ) {
        Instant deadline = Instant.now().plus(collectionWindow);
        int count = 0;
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(topic));
            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : polled) {
                    if (predicate.test(record)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public boolean containsText(ConsumerRecord<String, String> record, String text) {
        if (record.value() != null && record.value().contains(text)) {
            return true;
        }
        if (record.key() != null && record.key().contains(text)) {
            return true;
        }
        for (Header header : record.headers()) {
            if (header.value() != null
                && new String(header.value(), StandardCharsets.UTF_8).contains(text)) {
                return true;
            }
        }
        return false;
    }

    public String header(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        return header == null || header.value() == null
            ? null
            : new String(header.value(), StandardCharsets.UTF_8);
    }

    public String eventuatePayload(String wireValue) {
        if (wireValue == null) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(wireValue);
            if (node != null && node.has("payload")) {
                JsonNode payload = node.get("payload");
                return payload.isTextual() ? payload.asText() : payload.toString();
            }
        } catch (Exception ignored) {
            // Raw domain records are valid probe inputs too.
        }
        return wireValue;
    }

    public Map<String, String> textHeaders(Headers headers) {
        Map<String, String> result = new HashMap<>();
        for (Header header : headers) {
            if (header.value() != null) {
                result.put(
                    header.key(),
                    new String(header.value(), StandardCharsets.UTF_8)
                );
            }
        }
        return result;
    }

    private RecordMetadata send(ProducerRecord<String, String> record) {
        try {
            return producer.send(record).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to publish Kafka probe record", e);
        }
    }

    private KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(
            ConsumerConfig.GROUP_ID_CONFIG,
            "ftgo-phase03-probe-" + UUID.randomUUID()
        );
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return new KafkaConsumer<>(properties);
    }

    private RecordHeaders copyHeaders(Headers source) {
        RecordHeaders copy = new RecordHeaders();
        for (Header header : source) {
            copy.add(
                header.key(),
                header.value() == null ? null : header.value().clone()
            );
        }
        return copy;
    }

    @Override
    public void close() {
        producer.flush();
        producer.close(Duration.ofSeconds(10));
    }
}
