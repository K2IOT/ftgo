package net.ftgo.accounting.config;

import io.eventuate.common.json.mapper.JSonMapper;
import io.eventuate.messaging.kafka.common.EventuateKafkaMultiMessageConverter;
import io.eventuate.tram.consumer.common.MessageConsumerImplementation;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.messaging.common.MessageImpl;
import io.eventuate.tram.messaging.consumer.MessageHandler;
import io.eventuate.tram.messaging.consumer.MessageSubscription;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.accounting.settlement.SettlementGatewayTimeoutException;
import net.ftgo.accounting.settlement.SettlementRetryExhaustedException;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.KafkaDeadLetterSupport;
import net.ftgo.common.messaging.NonRetryableEventException;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration(proxyBeanMethods = false)
public class AccountingKafkaRetryConfiguration {

    @Bean("accountingKafkaProducerFactory")
    ProducerFactory<Object, Object> accountingKafkaProducerFactory(
        @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean("accountingKafkaTemplate")
    KafkaTemplate<Object, Object> accountingKafkaTemplate(
        @Qualifier("accountingKafkaProducerFactory") ProducerFactory<Object, Object> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean("accountingKafkaConsumerFactory")
    ConsumerFactory<String, byte[]> accountingKafkaConsumerFactory(
        @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean("accountingKafkaAdmin")
    KafkaAdmin accountingKafkaAdmin(
        @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        KafkaAdmin kafkaAdmin = new KafkaAdmin(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrapServers
        ));
        kafkaAdmin.setFatalIfBrokerNotAvailable(false);
        return kafkaAdmin;
    }

    @Bean
    NewTopic accountingServiceCommandTopic(
        @Value("${ftgo.accounting.kafka.topic-partitions:4}") int partitions
    ) {
        return topic(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL, partitions);
    }

    @Bean
    NewTopic accountingServiceCommandDeadLetterTopic(
        @Value("${ftgo.accounting.kafka.topic-partitions:4}") int partitions
    ) {
        return topic(
            ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL + KafkaDeadLetterSupport.DLT_SUFFIX,
            partitions
        );
    }

    @Bean("accountingSettlementKafkaErrorHandler")
    DefaultErrorHandler accountingSettlementKafkaErrorHandler(
        @Qualifier("accountingKafkaTemplate") KafkaTemplate<Object, Object> kafkaTemplate,
        MeterRegistry meterRegistry,
        @Value("${ftgo.accounting.settlement.retry-delays-ms:1000,5000,30000}")
        String retryDelays
    ) {
        DefaultErrorHandler handler = KafkaDeadLetterSupport.errorHandler(
            kafkaTemplate,
            meterRegistry,
            KafkaDeadLetterSupport.retryBackOff(parseRetryDelays(retryDelays))
        );
        handler.addRetryableExceptions(SettlementGatewayTimeoutException.class);
        handler.addNotRetryableExceptions(
            SettlementRetryExhaustedException.class,
            IllegalStateException.class
        );
        handler.setRetryListeners((record, exception, deliveryAttempt) ->
            meterRegistry.counter(
                "ftgo_accounting_settlement_redelivery_total",
                "topic", record.topic(),
                "exception", exception.getClass().getName(),
                "delivery_attempt", Integer.toString(deliveryAttempt)
            ).increment());
        return handler;
    }

    @Bean("accountingKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, byte[]> accountingKafkaListenerContainerFactory(
        @Qualifier("accountingKafkaConsumerFactory") ConsumerFactory<String, byte[]> consumerFactory,
        @Qualifier("accountingSettlementKafkaErrorHandler") DefaultErrorHandler errorHandler,
        @Value("${ftgo.accounting.kafka.concurrency:4}") int concurrency
    ) {
        if (concurrency <= 0) {
            throw new IllegalArgumentException("Accounting Kafka concurrency must be positive");
        }
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setMissingTopicsFatal(false);
        return factory;
    }

    @Bean(value = "accountingMessageConsumerImplementation", destroyMethod = "close")
    @Primary
    MessageConsumerImplementation accountingMessageConsumerImplementation(
        @Qualifier("accountingKafkaListenerContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, byte[]> containerFactory,
        @Qualifier("accountingKafkaAdmin") KafkaAdmin kafkaAdmin,
        @Qualifier("accountingServiceCommandTopic") NewTopic commandTopic,
        @Qualifier("accountingServiceCommandDeadLetterTopic") NewTopic deadLetterTopic
    ) {
        return new SpringKafkaMessageConsumerImplementation(
            containerFactory,
            kafkaAdmin,
            commandTopic,
            deadLetterTopic
        );
    }

    static long[] parseRetryDelays(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("Settlement retry delays cannot be blank");
        }
        try {
            return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .mapToLong(Long::parseLong)
                .toArray();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                "Settlement retry delays must be comma-separated milliseconds",
                exception
            );
        }
    }

    private static NewTopic topic(String name, int partitions) {
        if (partitions <= 0) {
            throw new IllegalArgumentException("Accounting Kafka partitions must be positive");
        }
        return TopicBuilder.name(name)
            .partitions(partitions)
            .replicas(1)
            .build();
    }

    static final class SpringKafkaMessageConsumerImplementation
        implements MessageConsumerImplementation {

        private final ConcurrentKafkaListenerContainerFactory<String, byte[]> containerFactory;
        private final KafkaAdmin kafkaAdmin;
        private final NewTopic commandTopic;
        private final NewTopic deadLetterTopic;
        private final EventuateKafkaMultiMessageConverter multiMessageConverter =
            new EventuateKafkaMultiMessageConverter();
        private final List<ConcurrentMessageListenerContainer<String, byte[]>> containers =
            new CopyOnWriteArrayList<>();
        private final AtomicInteger containerSequence = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean topicsReady = new AtomicBoolean();

        SpringKafkaMessageConsumerImplementation(
            ConcurrentKafkaListenerContainerFactory<String, byte[]> containerFactory,
            KafkaAdmin kafkaAdmin,
            NewTopic commandTopic,
            NewTopic deadLetterTopic
        ) {
            this.containerFactory = Objects.requireNonNull(
                containerFactory,
                "containerFactory"
            );
            this.kafkaAdmin = Objects.requireNonNull(kafkaAdmin, "kafkaAdmin");
            this.commandTopic = Objects.requireNonNull(commandTopic, "commandTopic");
            this.deadLetterTopic = Objects.requireNonNull(
                deadLetterTopic,
                "deadLetterTopic"
            );
        }

        @Override
        public MessageSubscription subscribe(
            String subscriberId,
            Set<String> channels,
            MessageHandler handler
        ) {
            if (closed.get()) {
                throw new IllegalStateException("Accounting Kafka consumer is closed");
            }
            if (subscriberId == null || subscriberId.isBlank()) {
                throw new IllegalArgumentException("Subscriber ID cannot be blank");
            }
            if (channels == null || channels.isEmpty()) {
                throw new IllegalArgumentException("At least one channel is required");
            }
            Objects.requireNonNull(handler, "handler");

            ensureTopicsReady();

            ConcurrentMessageListenerContainer<String, byte[]> container =
                containerFactory.createContainer(channels.toArray(String[]::new));
            container.setBeanName(
                "accounting-kafka-" + subscriberId + "-" + containerSequence.incrementAndGet()
            );
            container.getContainerProperties().setGroupId(subscriberId);
            container.getContainerProperties().setMessageListener(
                (MessageListener<String, byte[]>) record -> {
                    List<Message> messages = decode(record.value());
                    messages.forEach(handler::accept);
                }
            );
            containers.add(container);
            container.start();

            AtomicBoolean subscribed = new AtomicBoolean(true);
            return () -> {
                if (subscribed.compareAndSet(true, false)) {
                    containers.remove(container);
                    container.stop();
                }
            };
        }

        @Override
        public String getId() {
            return "accounting-spring-kafka-redelivery";
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            List<ConcurrentMessageListenerContainer<String, byte[]>> snapshot =
                new ArrayList<>(containers);
            containers.clear();
            snapshot.forEach(ConcurrentMessageListenerContainer::stop);
        }

        private void ensureTopicsReady() {
            if (topicsReady.get()) {
                return;
            }
            synchronized (topicsReady) {
                if (topicsReady.get()) {
                    return;
                }
                kafkaAdmin.createOrModifyTopics(commandTopic, deadLetterTopic);
                topicsReady.set(true);
            }
        }

        private List<Message> decode(byte[] payload) {
            if (payload == null) {
                throw new NonRetryableEventException("Kafka message payload cannot be null");
            }
            try {
                return multiMessageConverter.convertBytesToValues(payload).stream()
                    .map(value -> (Message) JSonMapper.fromJson(value, MessageImpl.class))
                    .toList();
            } catch (RuntimeException exception) {
                throw new NonRetryableEventException(
                    "Cannot decode Eventuate Kafka message",
                    exception
                );
            }
        }
    }
}
