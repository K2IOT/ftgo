package net.ftgo.consumer.integration;

import io.eventuate.tram.commands.common.CommandMessageHeaders;
import io.eventuate.tram.commands.common.DefaultCommandNameMapping;
import io.eventuate.tram.commands.common.ReplyMessageHeaders;
import io.eventuate.tram.commands.consumer.CommandHandler;
import io.eventuate.tram.commands.consumer.CommandHandlerArgs;
import io.eventuate.tram.commands.consumer.CommandHandlerParams;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.commands.consumer.CommandReplyToken;
import io.eventuate.tram.commands.consumer.PathVariables;
import io.eventuate.tram.commands.producer.CommandMessageFactory;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.messaging.consumer.MessageConsumer;
import io.eventuate.tram.messaging.producer.MessageProducer;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.VerifyConsumerCommand;
import net.ftgo.common.orderflow.replies.ConsumerVerified;
import net.ftgo.consumer.ConsumerServiceApplication;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.messaging.ConsumerCommandHandlers;
import net.ftgo.consumer.messaging.OutboxEntry;
import net.ftgo.consumer.messaging.OutboxRepository;
import net.ftgo.consumer.repository.ConsumerRepository;
import net.ftgo.consumer.service.ConsumerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = ConsumerServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
@Testcontainers
class ConsumerServiceIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("consumer_service")
        .withUsername("test")
        .withPassword("test");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eventuatelocal.kafka.bootstrap.servers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> "https://identity.example/realms/ftgo"
        );
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "https://identity.example/realms/ftgo/protocol/openid-connect/certs"
        );
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ConsumerRepository consumerRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @MockitoSpyBean
    private ConsumerService consumerService;

    @MockitoSpyBean
    private ConsumerCommandHandlers consumerCommandHandlers;

    @MockitoBean
    private MessageProducer messageProducer;

    @MockitoBean
    private MessageConsumer messageConsumer;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private String consumersUrl;

    @BeforeEach
    void setUp() {
        consumersUrl = "http://localhost:" + port + "/consumers";
        outboxRepository.deleteAll();
        consumerRepository.deleteAll();
        reset(consumerService, consumerCommandHandlers);
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> jwtFor(invocation.getArgument(0)));
    }

    @Test
    void containersStart() {
        assertThat(mysql.isRunning()).isTrue();
        assertThat(kafka.isRunning()).isTrue();
    }

    @Test
    void restCreateReadAndUpdateGoThroughServiceAndPersistBusinessState() {
        ResponseEntity<Map> createResponse = restTemplate.exchange(
            consumersUrl,
            HttpMethod.POST,
            jsonRequest(Map.of(
                "name", "Jane Consumer",
                "email", "jane.consumer@example.com",
                "creditLimit", "1000.00"
            ), "consumer-token-1"),
            Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        Long consumerId = ((Number) createResponse.getBody().get("id")).longValue();

        verify(consumerService).createConsumer(
            eq("Jane Consumer"),
            eq("jane.consumer@example.com"),
            eq(money("100.00"))
        );

        Consumer created = consumerRepository.findById(consumerId).orElseThrow();
        assertThat(created.getName()).isEqualTo("Jane Consumer");
        assertThat(created.getEmail()).isEqualTo("jane.consumer@example.com");
        assertThat(created.getCreditLimit()).isEqualTo(money("100.00"));
        assertThat(created.getAvailableCredit()).isEqualTo(money("100.00"));

        String ownerToken = "consumer-token-" + consumerId;
        ResponseEntity<Map> readResponse = restTemplate.exchange(
            consumersUrl + "/" + consumerId,
            HttpMethod.GET,
            bearerRequest(ownerToken),
            Map.class
        );

        assertThat(readResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readResponse.getBody()).containsEntry("name", "Jane Consumer");
        verify(consumerService).findConsumer(eq(consumerId));

        ResponseEntity<Map> updateResponse = restTemplate.exchange(
            consumersUrl + "/" + consumerId,
            HttpMethod.PUT,
            jsonRequest(Map.of(
                "name", "Jane Updated",
                "email", "jane.updated@example.com"
            ), ownerToken),
            Map.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(consumerService).updateConsumer(
            eq(consumerId),
            eq("Jane Updated"),
            eq("jane.updated@example.com")
        );

        Consumer updated = consumerRepository.findById(consumerId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Jane Updated");
        assertThat(updated.getEmail()).isEqualTo("jane.updated@example.com");
        assertThat(updated.getCreditLimit()).isEqualTo(money("100.00"));
        assertThat(updated.getAvailableCredit()).isEqualTo(money("100.00"));

        List<OutboxEntry> outboxEntries = outboxRepository.findAll();
        assertThat(outboxEntries).hasSize(1);
        OutboxEntry event = outboxEntries.getFirst();
        assertThat(event.getAggregateType()).isEqualTo("Consumer");
        assertThat(event.getAggregateId()).isEqualTo(consumerId.toString());
        assertThat(event.getEventType()).isEqualTo("ConsumerUpdated");
        assertThat(event.getDestination()).isEqualTo(ChannelNames.CONSUMER_EVENT_TOPIC);
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPayload()).contains(
            "\"consumerId\": " + consumerId,
            "\"name\": \"Jane Updated\"",
            "\"email\": \"jane.updated@example.com\""
        );
    }

    @Test
    void kafkaCommandHandlerConsumesVerifyConsumerCommandAndCallsService() {
        Consumer consumer = consumerRepository.save(
            new Consumer("Command Consumer", "command.consumer@example.com", money("75.00"))
        );

        Message reply = handleVerifyConsumerCommand(consumer.getId(), money("25.00"));

        verify(consumerCommandHandlers).commandHandlers();
        verify(consumerService).verifyConsumerCredit(eq(consumer.getId()), eq(money("25.00")));
        assertThat(reply.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME)).isEqualTo("SUCCESS");
        assertThat(reply.getRequiredHeader(ReplyMessageHeaders.REPLY_TYPE))
            .isEqualTo(ConsumerVerified.class.getName());
        assertThat(reply.getPayload()).contains("\"consumerId\":" + consumer.getId());
    }

    @Test
    void kafkaCommandHandlerReturnsFailureWhenServiceRejectsCommand() {
        Consumer consumer = consumerRepository.save(
            new Consumer("Low Credit Consumer", "low.credit@example.com", money("20.00"))
        );

        Message reply = handleVerifyConsumerCommand(consumer.getId(), money("25.00"));

        verify(consumerService).verifyConsumerCredit(eq(consumer.getId()), eq(money("25.00")));
        assertThat(reply.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME)).isEqualTo("FAILURE");
        assertThat(reply.getPayload()).contains("Insufficient credit limit");
    }

    @Test
    void serviceRejectsDuplicateEmailWithoutAddingAnotherRow() {
        consumerService.createConsumer("Original", "duplicate@example.com", money("50.00"));

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> consumerService.createConsumer("Duplicate", "duplicate@example.com", money("75.00"))
        );

        assertThat(consumerRepository.findAll())
            .singleElement()
            .extracting(Consumer::getEmail)
            .isEqualTo("duplicate@example.com");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Message handleVerifyConsumerCommand(Long consumerId, Money orderTotal) {
        Message message = CommandMessageFactory.makeMessage(
            new DefaultCommandNameMapping(),
            ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL,
            new VerifyConsumerCommand(consumerId, orderTotal),
            "consumerServiceIntegrationReplies",
            Map.of()
        );
        message.setHeader(Message.ID, UUID.randomUUID().toString());

        assertThat(message.getRequiredHeader(CommandMessageHeaders.DESTINATION))
            .isEqualTo(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL);

        CommandHandlers commandHandlers = consumerCommandHandlers.commandHandlers();
        CommandHandler commandHandler = commandHandlers.findTargetMethod(message).orElseThrow();
        CommandHandlerParams params = new CommandHandlerParams(
            message,
            commandHandler.getCommandClass(),
            commandHandler.getResource()
        );
        CommandMessage<VerifyConsumerCommand> commandMessage = new CommandMessage<>(
            message.getId(),
            (VerifyConsumerCommand) params.getCommand(),
            params.getCorrelationHeaders(),
            message
        );
        CommandHandlerArgs<VerifyConsumerCommand> args = new CommandHandlerArgs<>(
            commandMessage,
            new PathVariables(params.getPathVars()),
            new CommandReplyToken(params.getCorrelationHeaders(), "consumerServiceIntegrationReplies")
        );

        List<Message> replies = commandHandler.invokeMethod(args);
        assertThat(replies).hasSize(1);
        return replies.getFirst();
    }

    private HttpEntity<?> jsonRequest(Object body, String token) {
        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<?> bearerRequest(String token) {
        return new HttpEntity<>(bearerHeaders(token));
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Jwt jwtFor(String token) {
        String prefix = "consumer-token-";
        long consumerId = token.startsWith(prefix)
            ? Long.parseLong(token.substring(prefix.length()))
            : 1L;
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .issuer("https://identity.example/realms/ftgo")
            .subject("consumer-" + consumerId)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of("CONSUMER"))
            .claim("consumer_id", consumerId)
            .build();
    }

    private static Money money(String amount) {
        return new Money(amount);
    }
}
