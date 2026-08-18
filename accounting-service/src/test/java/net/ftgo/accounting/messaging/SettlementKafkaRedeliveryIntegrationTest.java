package net.ftgo.accounting.messaging;

import io.eventuate.common.json.mapper.JSonMapper;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.consumer.common.MessageConsumerImplementation;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.messaging.common.MessageImpl;
import io.eventuate.tram.messaging.consumer.MessageSubscription;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.config.AccountingKafkaRetryConfiguration;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.payment.PaymentAuthorizationGateway;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.accounting.settlement.PaymentLedgerService;
import net.ftgo.accounting.settlement.SettlementDecision;
import net.ftgo.accounting.settlement.SettlementGateway;
import net.ftgo.accounting.settlement.SettlementGatewayTimeoutException;
import net.ftgo.accounting.settlement.SettlementReconciliationWorkRepository;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.testsupport.InMemoryProcessedCommandStore;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {
    AccountingKafkaRetryConfiguration.class,
    SettlementKafkaRedeliveryIntegrationTest.TestBeans.class
})
@EmbeddedKafka(
    partitions = 1,
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

    static final String TOPIC = ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL;
    private static final String GROUP_ID = "settlement-redelivery-integration";
    private static final String CONSUMER_NAME = "accounting-service";

    @Autowired
    private MessageConsumerImplementation messageConsumer;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private MeterRegistry meterRegistry;

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final PaymentAuthorizationGateway paymentAuthorizationGateway =
        mock(PaymentAuthorizationGateway.class);
    private final SettlementGateway settlementGateway = mock(SettlementGateway.class);
    private final PaymentLedgerService paymentLedgerService = mock(PaymentLedgerService.class);
    private final SettlementReconciliationWorkRepository reconciliationWorkRepository =
        mock(SettlementReconciliationWorkRepository.class);

    private InMemoryProcessedCommandStore processedCommands;
    private AccountingServiceCommandHandlers handlers;
    private MessageSubscription subscription;

    @BeforeEach
    void setUp() {
        processedCommands = new InMemoryProcessedCommandStore();
        handlers = new AccountingServiceCommandHandlers(
            accountRepository,
            eventPublisher,
            paymentAuthorizationGateway,
            settlementGateway,
            paymentLedgerService,
            reconciliationWorkRepository,
            new IdempotentCommandExecutor(processedCommands)
        );
    }

    @AfterEach
    void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Test
    void redeliversTimedOutAccountingCommandAndEmitsOneStableReply() throws Exception {
        Account retryAccount = accountWithAuthorization(101L, 701L, new Money("42.50"));
        Account unrelatedAccount = accountWithAuthorization(102L, 702L, new Money("25.00"));
        when(accountRepository.findByAuthorizationId(701L))
            .thenReturn(Optional.of(retryAccount));
        when(accountRepository.findByAuthorizationId(702L))
            .thenReturn(Optional.of(unrelatedAccount));
        when(accountRepository.saveAndFlush(any(Account.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CountDownLatch firstTimeoutObserved = new CountDownLatch(1);
        CountDownLatch retryReplyEmitted = new CountDownLatch(1);
        CountDownLatch unrelatedReplyEmitted = new CountDownLatch(1);
        AtomicInteger providerAttempts = new AtomicInteger();
        Map<String, AtomicInteger> replyCounts = new ConcurrentHashMap<>();

        when(settlementGateway.capture(701L, 101L, "capture-retry-101"))
            .thenAnswer(invocation -> {
                if (providerAttempts.incrementAndGet() == 1) {
                    firstTimeoutObserved.countDown();
                    throw new SettlementGatewayTimeoutException("provider timeout");
                }
                return SettlementDecision.approved("provider-capture-101");
            });
        when(settlementGateway.capture(702L, 102L, "capture-unrelated-102"))
            .thenReturn(SettlementDecision.approved("provider-capture-102"));

        Map<String, CommandMessage<CaptureAuthorizationCommand>> commands = Map.of(
            "retry",
            commandMessage(
                "command-capture-101",
                new CaptureAuthorizationCommand(101L, 701L, "capture-retry-101")
            ),
            "unrelated",
            commandMessage(
                "command-capture-102",
                new CaptureAuthorizationCommand(102L, 702L, "capture-unrelated-102")
            )
        );

        subscription = messageConsumer.subscribe(
            GROUP_ID,
            Set.of(TOPIC),
            kafkaMessage -> {
                Message reply = handlers.handleCaptureAuthorization(
                    commands.get(kafkaMessage.getId())
                );
                replyCounts.computeIfAbsent(
                    kafkaMessage.getId(),
                    ignored -> new AtomicInteger()
                ).incrementAndGet();
                if (kafkaMessage.getId().equals("retry")) {
                    retryReplyEmitted.countDown();
                } else {
                    unrelatedReplyEmitted.countDown();
                }
                assertThat(reply).isNotNull();
            }
        );

        awaitStableAssignment(GROUP_ID, 4, Duration.ofSeconds(10));
        send(0, "retry");
        assertThat(firstTimeoutObserved.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(processedCommands.findCompleted(
            CONSUMER_NAME,
            "command-capture-101"
        )).isEmpty();
        assertThat(retryAccount.findAuthorizationById(701L).getStatus())
            .isEqualTo(AuthorizationStatus.AUTHORIZED);

        long unrelatedStartedAt = System.nanoTime();
        send(1, "unrelated");
        assertThat(unrelatedReplyEmitted.await(2, TimeUnit.SECONDS))
            .as("another accounting command must progress during settlement backoff")
            .isTrue();
        Duration unrelatedLatency = Duration.ofNanos(
            System.nanoTime() - unrelatedStartedAt
        );

        assertThat(retryReplyEmitted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(unrelatedLatency).isLessThan(Duration.ofSeconds(2));
        assertThat(providerAttempts).hasValue(2);
        assertThat(replyCounts.get("retry")).hasValue(1);
        assertThat(replyCounts.get("unrelated")).hasValue(1);
        assertThat(retryAccount.findAuthorizationById(701L).getStatus())
            .isEqualTo(AuthorizationStatus.CAPTURED);
        assertThat(unrelatedAccount.findAuthorizationById(702L).getStatus())
            .isEqualTo(AuthorizationStatus.CAPTURED);
        assertThat(processedCommands.findCompleted(
            CONSUMER_NAME,
            "command-capture-101"
        )).isPresent();
        assertThat(processedCommands.findCompleted(
            CONSUMER_NAME,
            "command-capture-102"
        )).isPresent();
        assertThat(
            meterRegistry.find("ftgo_accounting_settlement_redelivery_total").counter()
        ).isNotNull();

        verify(settlementGateway, times(2)).capture(
            701L,
            101L,
            "capture-retry-101"
        );
        verify(settlementGateway, times(1)).capture(
            702L,
            102L,
            "capture-unrelated-102"
        );
        verify(accountRepository, times(1)).saveAndFlush(retryAccount);
        verify(accountRepository, times(1)).saveAndFlush(unrelatedAccount);
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
                    boolean onePartitionPerMember = description.members().stream()
                        .allMatch(member -> member.assignment().topicPartitions().size() == 1);
                    if (description.members().size() == expectedMembers
                        && assignedPartitions == expectedMembers
                        && onePartitionPerMember) {
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
        Message message = new MessageImpl("{}", Map.of(Message.ID, messageId));
        byte[] payload = JSonMapper.toJson(message).getBytes(StandardCharsets.UTF_8);
        kafkaTemplate.send(new ProducerRecord<>(TOPIC, partition, messageId, payload))
            .get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();
    }

    private Account accountWithAuthorization(Long orderId, Long authorizationId, Money amount) {
        Account account = new Account(301L);
        ReflectionTestUtils.setField(account, "id", authorizationId - 200L);
        ReflectionTestUtils.setField(account, "version", 0L);
        Authorization authorization = account.authorize(
            orderId,
            "authorize-" + orderId,
            amount
        );
        ReflectionTestUtils.setField(authorization, "id", authorizationId);
        return account;
    }

    private CommandMessage<CaptureAuthorizationCommand> commandMessage(
        String messageId,
        CaptureAuthorizationCommand command
    ) {
        return new CommandMessage<>(
            messageId,
            command,
            Map.of(),
            mock(Message.class)
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
