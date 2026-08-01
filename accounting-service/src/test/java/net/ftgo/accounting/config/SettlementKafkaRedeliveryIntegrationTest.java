package net.ftgo.accounting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventuate.tram.consumer.common.MessageConsumerImplementation;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.messaging.common.MessageImpl;
import io.eventuate.tram.messaging.consumer.MessageSubscription;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.settlement.SettlementGatewayTimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {
    AccountingKafkaRetryConfiguration.class,
    SettlementKafkaRedeliveryIntegrationTest.TestBeans.class
})
@EmbeddedKafka(
    partitions = 4,
    topics = {
        SettlementKafkaRedeliveryIntegrationTest.TOPIC,
        SettlementKafkaRedeliveryIntegrationTest.TOPIC + ".DLT"
    },
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
    "ftgo.accounting.settlement.retry-delays-ms=3000,100,100",
    "ftgo.accounting.kafka.concurrency=4",
    "ftgo.accounting.kafka.topic-partitions=4"
})
@DirtiesContext
class SettlementKafkaRedeliveryIntegrationTest {

    static final String TOPIC = "settlement-redelivery-test";
    private static final String GROUP_ID = "settlement-redelivery-integration";

    @Autowired
    private MessageConsumerImplementation messageConsumer;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private MeterRegistry meterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MessageSubscription subscription;

    @AfterEach
    void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Test
    void retriesTimedOutRecordWhileAnotherPartitionKeepsMakingProgress() throws Exception {
        CountDownLatch warmupCompleted = new CountDownLatch(1);
        CountDownLatch firstTimeoutObserved = new CountDownLatch(1);
        CountDownLatch unrelatedCompleted = new CountDownLatch(1);
        CountDownLatch retryCompleted = new CountDownLatch(1);
        AtomicInteger retryAttempts = new AtomicInteger();
        AtomicInteger retrySuccesses = new AtomicInteger();
        AtomicInteger unrelatedSuccesses = new AtomicInteger();

        subscription = messageConsumer.subscribe(
            GROUP_ID,
            Set.of(TOPIC),
            message -> {
                switch (message.getId()) {
                    case "warmup" -> warmupCompleted.countDown();
                    case "retry" -> {
                        int attempt = retryAttempts.incrementAndGet();
                        if (attempt == 1) {
                            firstTimeoutObserved.countDown();
                            throw new SettlementGatewayTimeoutException("provider timeout");
                        }
                        retrySuccesses.incrementAndGet();
                        retryCompleted.countDown();
                    }
                    case "unrelated" -> {
                        unrelatedSuccesses.incrementAndGet();
                        unrelatedCompleted.countDown();
                    }
                    default -> throw new IllegalArgumentException(
                        "Unexpected message " + message.getId()
                    );
                }
            }
        );

        awaitStableAssignment(GROUP_ID, 4, Duration.ofSeconds(10));
        send(3, "warmup");
        assertThat(warmupCompleted.await(10, TimeUnit.SECONDS)).isTrue();

        send(0, "retry");
        assertThat(firstTimeoutObserved.await(10, TimeUnit.SECONDS)).isTrue();

        long unrelatedStartedAt = System.nanoTime();
        send(1, "unrelated");
        assertThat(unrelatedCompleted.await(2, TimeUnit.SECONDS))
            .as("another partition must progress while the timed-out record is backing off")
            .isTrue();
        Duration unrelatedLatency = Duration.ofNanos(
            System.nanoTime() - unrelatedStartedAt
        );

        assertThat(retryCompleted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(unrelatedLatency).isLessThan(Duration.ofSeconds(2));
        assertThat(retryAttempts).hasValue(2);
        assertThat(retrySuccesses).hasValue(1);
        assertThat(unrelatedSuccesses).hasValue(1);
        assertThat(
            meterRegistry.find("ftgo_accounting_settlement_redelivery_total").counter()
        ).isNotNull();
    }

    private void awaitStableAssignment(
        String groupId,
        int expectedMembers,
        Duration timeout
    ) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception lastFailure = null;
        try (Admin admin = Admin.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            embeddedKafkaBroker.getBrokersAsString()
        ))) {
            while (System.nanoTime() < deadline) {
                try {
                    var description = admin.describeConsumerGroups(List.of(groupId))
                        .all()
                        .get(1, TimeUnit.SECONDS)
                        .get(groupId);
                    int assignedPartitions = description.members().stream()
                        .mapToInt(member -> member.assignment().topicPartitions().size())
                        .sum();
                    if (description.members().size() == expectedMembers
                        && assignedPartitions == expectedMembers) {
                        return;
                    }
                } catch (Exception exception) {
                    lastFailure = exception;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
                if (Thread.interrupted()) {
                    throw new InterruptedException(
                        "Interrupted while waiting for Kafka consumer assignment"
                    );
                }
            }
        }
        throw new AssertionError(
            "Kafka group " + groupId + " did not stabilize at "
                + expectedMembers + " members/partitions within " + timeout,
            lastFailure
        );
    }

    private void send(int partition, String messageId) throws Exception {
        Message message = new MessageImpl(
            "{}",
            Map.of(Message.ID, messageId)
        );
        byte[] payload = objectMapper.writeValueAsBytes(message);
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
