package net.ftgo.accounting.config;

import io.eventuate.common.json.mapper.JSonMapper;
import io.eventuate.tram.consumer.common.MessageConsumerImplementation;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.messaging.common.MessageImpl;
import io.eventuate.tram.messaging.consumer.MessageSubscription;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.settlement.SettlementGatewayTimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {
    AccountingKafkaRetryConfiguration.class,
    SettlementKafkaRedeliveryLoadTest.TestBeans.class
})
@EmbeddedKafka(
    partitions = 4,
    topics = {
        SettlementKafkaRedeliveryLoadTest.TOPIC,
        SettlementKafkaRedeliveryLoadTest.TOPIC + ".DLT"
    },
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
    "ftgo.accounting.settlement.retry-delays-ms=25,50,100",
    "ftgo.accounting.kafka.concurrency=4",
    "ftgo.accounting.kafka.topic-partitions=4"
})
@DirtiesContext
class SettlementKafkaRedeliveryLoadTest {

    static final String TOPIC = "settlement-redelivery-load-test";
    private static final int TIMEOUT_COMMANDS = 20;

    @Autowired
    private MessageConsumerImplementation messageConsumer;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    private MessageSubscription subscription;

    @AfterEach
    void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Test
    void boundsTwentyPermanentTimeoutsWithoutStarvingUnrelatedTraffic() throws Exception {
        CountDownLatch warmupCompleted = new CountDownLatch(1);
        CountDownLatch firstWaveTimedOut = new CountDownLatch(3);
        CountDownLatch unrelatedCompleted = new CountDownLatch(1);
        CountDownLatch terminalReplies = new CountDownLatch(TIMEOUT_COMMANDS);
        Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> terminalReplyCounts = new ConcurrentHashMap<>();

        subscription = messageConsumer.subscribe(
            "settlement-redelivery-load",
            Set.of(TOPIC),
            message -> {
                String messageId = message.getId();
                if (messageId.equals("warmup")) {
                    warmupCompleted.countDown();
                    return;
                }
                if (messageId.equals("unrelated")) {
                    unrelatedCompleted.countDown();
                    return;
                }
                if (!messageId.startsWith("timeout-")) {
                    throw new IllegalArgumentException("Unexpected message " + messageId);
                }

                int attempt = attempts.computeIfAbsent(
                    messageId,
                    ignored -> new AtomicInteger()
                ).incrementAndGet();
                if (attempt <= 3) {
                    if (attempt == 1) {
                        firstWaveTimedOut.countDown();
                    }
                    throw new SettlementGatewayTimeoutException("provider timeout");
                }

                terminalReplyCounts.computeIfAbsent(
                    messageId,
                    ignored -> new AtomicInteger()
                ).incrementAndGet();
                terminalReplies.countDown();
            }
        );

        send(3, "warmup");
        assertThat(warmupCompleted.await(10, TimeUnit.SECONDS)).isTrue();

        for (int index = 0; index < TIMEOUT_COMMANDS; index++) {
            send(index % 3, "timeout-" + index);
        }
        assertThat(firstWaveTimedOut.await(10, TimeUnit.SECONDS)).isTrue();

        long unrelatedStartedAt = System.nanoTime();
        send(3, "unrelated");
        assertThat(unrelatedCompleted.await(2, TimeUnit.SECONDS))
            .as("the reserved partition must remain available during settlement backoff")
            .isTrue();
        Duration unrelatedLatency = Duration.ofNanos(
            System.nanoTime() - unrelatedStartedAt
        );

        assertThat(terminalReplies.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(unrelatedLatency).isLessThan(Duration.ofSeconds(2));
        assertThat(attempts).hasSize(TIMEOUT_COMMANDS);
        assertThat(attempts.values())
            .allSatisfy(attempt -> assertThat(attempt).hasValue(4));
        assertThat(terminalReplyCounts).hasSize(TIMEOUT_COMMANDS);
        assertThat(terminalReplyCounts.values())
            .allSatisfy(count -> assertThat(count).hasValue(1));
        assertThat(
            meterRegistry.find("ftgo_accounting_settlement_redelivery_total")
                .counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum()
        ).isEqualTo(TIMEOUT_COMMANDS * 3.0);
    }

    private void send(int partition, String messageId) throws Exception {
        Message message = new MessageImpl("{}", Map.of(Message.ID, messageId));
        byte[] payload = JSonMapper.toJson(message).getBytes(StandardCharsets.UTF_8);
        kafkaTemplate.send(new ProducerRecord<>(TOPIC, partition, messageId, payload))
            .get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
