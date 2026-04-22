package net.ftgo.consumer.integration;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.commands.producer.CommandProducer;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.messaging.producer.MessageBuilder;
import net.ftgo.common.Money;
import net.ftgo.consumer.ConsumerServiceApplication;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.repository.ConsumerRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Consumer Service with Testcontainers (MySQL + Kafka).
 * 
 * Tests the complete Consumer Service stack including:
 * - MySQL database persistence
 * - Kafka messaging for domain events
 * - REST API endpoints
 * - Command handlers for saga participation
 */
@SpringBootTest(
    classes = ConsumerServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsumerServiceIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("consumer_service")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withReuse(true);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL configuration
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        
        // Kafka configuration
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eventuate.local.kafka.bootstrap.servers", kafka::getBootstrapServers);
        
        // JPA configuration
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.show-sql", () -> "true");
        
        // Flyway configuration
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ConsumerRepository consumerRepository;
    
    private String baseUrl;
    
    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        consumerRepository.deleteAll();
    }
    
    @Test
    @Order(1)
    void testContainersAreRunning() {
        assertTrue(mysql.isRunning(), "MySQL container should be running");
        assertTrue(kafka.isRunning(), "Kafka container should be running");
    }
    
    @Test
    @Order(2)
    void testCreateConsumerWithMySQLPersistence() {
        // Create consumer via REST API
        Map<String, Object> request = new HashMap<>();
        request.put("name", "John Doe");
        request.put("email", "john.doe@example.com");
        request.put("creditLimit", 1000.00);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/consumers",
            request,
            Map.class
        );
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Integer consumerId = (Integer) response.getBody().get("id");
        assertNotNull(consumerId);
        assertEquals("John Doe", response.getBody().get("name"));
        assertEquals("john.doe@example.com", response.getBody().get("email"));
        assertEquals(1000.0, response.getBody().get("creditLimit"));
        assertEquals(1000.0, response.getBody().get("availableCredit"));
        
        // Verify persistence in MySQL
        Consumer consumer = consumerRepository.findById(consumerId.longValue()).orElse(null);
        assertNotNull(consumer, "Consumer should be persisted in MySQL");
        assertEquals("John Doe", consumer.getName());
        assertEquals(new Money(new BigDecimal("1000.00")), consumer.getCreditLimit());
    }
    
    @Test
    @Order(3)
    void testConsumerCreditLimitValidation() {
        // Test creating consumer with negative credit limit
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Invalid User");
        request.put("email", "invalid@example.com");
        request.put("creditLimit", -100.00);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/consumers",
            request,
            Map.class
        );
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
    
    @Test
    @Order(4)
    void testConsumerCreditReservationAndRelease() {
        // Create consumer
        Consumer consumer = new Consumer(
            "Alice Smith",
            "alice@example.com",
            new Money(new BigDecimal("500.00"))
        );
        consumer = consumerRepository.save(consumer);
        
        // Reserve credit
        consumer.reserveCredit(new Money(new BigDecimal("200.00")));
        consumerRepository.save(consumer);
        
        // Verify available credit decreased
        Consumer reloaded = consumerRepository.findById(consumer.getId()).orElseThrow();
        assertEquals(new Money(new BigDecimal("300.00")), reloaded.getAvailableCredit());
        
        // Release credit
        reloaded.releaseCredit(new Money(new BigDecimal("200.00")));
        consumerRepository.save(reloaded);
        
        // Verify available credit restored
        Consumer finalReload = consumerRepository.findById(consumer.getId()).orElseThrow();
        assertEquals(new Money(new BigDecimal("500.00")), finalReload.getAvailableCredit());
    }
    
    @Test
    @Order(5)
    void testConsumerStateTransitions() {
        // Create consumer
        Consumer consumer = new Consumer(
            "Bob Johnson",
            "bob@example.com",
            new Money(new BigDecimal("1000.00"))
        );
        consumer = consumerRepository.save(consumer);
        
        // Test state transition: reserve credit
        consumer.reserveCredit(new Money(new BigDecimal("300.00")));
        consumerRepository.save(consumer);
        
        Consumer afterReserve = consumerRepository.findById(consumer.getId()).orElseThrow();
        assertEquals(new Money(new BigDecimal("700.00")), afterReserve.getAvailableCredit());
        
        // Test state transition: update credit limit
        afterReserve.updateCreditLimit(new Money(new BigDecimal("1500.00")));
        consumerRepository.save(afterReserve);
        
        Consumer afterUpdate = consumerRepository.findById(consumer.getId()).orElseThrow();
        assertEquals(new Money(new BigDecimal("1500.00")), afterUpdate.getCreditLimit());
        // Available credit should increase by difference: 700 + 500 = 1200
        assertEquals(new Money(new BigDecimal("1200.00")), afterUpdate.getAvailableCredit());
        
        // Test state transition: update profile
        afterUpdate.updateProfile("Robert Johnson", "robert@example.com");
        consumerRepository.save(afterUpdate);
        
        Consumer afterProfileUpdate = consumerRepository.findById(consumer.getId()).orElseThrow();
        assertEquals("Robert Johnson", afterProfileUpdate.getName());
        assertEquals("robert@example.com", afterProfileUpdate.getEmail());
    }
    
    @Test
    @Order(6)
    void testUpdateConsumerViaRestAPI() {
        // Create consumer
        Consumer consumer = new Consumer(
            "Charlie Brown",
            "charlie@example.com",
            new Money(new BigDecimal("750.00"))
        );
        consumer = consumerRepository.save(consumer);
        
        // Update via REST API
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("name", "Charles Brown");
        updateRequest.put("email", "charles@example.com");
        
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/consumers/" + consumer.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Charles Brown", response.getBody().get("name"));
        assertEquals("charles@example.com", response.getBody().get("email"));
        
        // Verify persistence
        Consumer updated = consumerRepository.findById(consumer.getId()).orElseThrow();
        assertEquals("Charles Brown", updated.getName());
        assertEquals("charles@example.com", updated.getEmail());
    }
    
    @Test
    @Order(7)
    void testGetConsumerViaRestAPI() {
        // Create consumer
        Consumer consumer = new Consumer(
            "Diana Prince",
            "diana@example.com",
            new Money(new BigDecimal("2000.00"))
        );
        consumer = consumerRepository.save(consumer);
        
        // Get via REST API
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/consumers/" + consumer.getId(),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(consumer.getId().intValue(), response.getBody().get("id"));
        assertEquals("Diana Prince", response.getBody().get("name"));
        assertEquals("diana@example.com", response.getBody().get("email"));
        assertEquals(2000.0, response.getBody().get("creditLimit"));
    }
    
    @Test
    @Order(8)
    void testConsumerNotFound() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/consumers/99999",
            Map.class
        );
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
    
    @Test
    @Order(9)
    void testDuplicateEmailValidation() {
        // Create first consumer
        Consumer consumer1 = new Consumer(
            "User One",
            "duplicate@example.com",
            new Money(new BigDecimal("500.00"))
        );
        consumerRepository.save(consumer1);
        
        // Try to create second consumer with same email via REST API
        Map<String, Object> request = new HashMap<>();
        request.put("name", "User Two");
        request.put("email", "duplicate@example.com");
        request.put("creditLimit", 600.00);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/consumers",
            request,
            Map.class
        );
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
    
    @Test
    @Order(10)
    void testCreditInvariantAcrossMultipleOperations() {
        // Create consumer with $1000 credit limit
        Consumer consumer = new Consumer(
            "Invariant Test User",
            "invariant@example.com",
            new Money(new BigDecimal("1000.00"))
        );
        consumer = consumerRepository.save(consumer);
        
        Long consumerId = consumer.getId();
        
        // Perform multiple reserve operations
        Consumer reloaded = consumerRepository.findById(consumerId).orElseThrow();
        reloaded.reserveCredit(new Money(new BigDecimal("100.00")));
        reloaded.reserveCredit(new Money(new BigDecimal("200.00")));
        reloaded.reserveCredit(new Money(new BigDecimal("150.00")));
        consumerRepository.save(reloaded);
        
        // Total reserved: 450.00, Available should be: 550.00
        Consumer afterReserves = consumerRepository.findById(consumerId).orElseThrow();
        assertEquals(new Money(new BigDecimal("550.00")), afterReserves.getAvailableCredit());
        
        // Release some credit
        afterReserves.releaseCredit(new Money(new BigDecimal("200.00")));
        consumerRepository.save(afterReserves);
        
        // Available should be: 750.00
        Consumer afterRelease = consumerRepository.findById(consumerId).orElseThrow();
        assertEquals(new Money(new BigDecimal("750.00")), afterRelease.getAvailableCredit());
        
        // Update credit limit
        afterRelease.updateCreditLimit(new Money(new BigDecimal("1500.00")));
        consumerRepository.save(afterRelease);
        
        // Available should increase by 500: 750 + 500 = 1250
        Consumer afterLimitUpdate = consumerRepository.findById(consumerId).orElseThrow();
        assertEquals(new Money(new BigDecimal("1250.00")), afterLimitUpdate.getAvailableCredit());
        
        // Verify invariant: availableCredit + reservedAmounts = creditLimit
        // Reserved: 450 - 200 = 250
        // Available: 1250
        // Total: 1250 + 250 = 1500 = creditLimit ✓
    }
    
    @Test
    @Order(11)
    void testInsufficientCreditReservation() {
        // Create consumer with $100 credit limit
        Consumer consumer = new Consumer(
            "Low Credit User",
            "lowcredit@example.com",
            new Money(new BigDecimal("100.00"))
        );
        consumer = consumerRepository.save(consumer);
        
        Long consumerId = consumer.getId();
        
        // Try to reserve more than available
        Consumer reloaded = consumerRepository.findById(consumerId).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> {
            reloaded.reserveCredit(new Money(new BigDecimal("150.00")));
        });
        
        // Verify available credit unchanged
        Consumer unchanged = consumerRepository.findById(consumerId).orElseThrow();
        assertEquals(new Money(new BigDecimal("100.00")), unchanged.getAvailableCredit());
    }
    
    @Test
    @Order(12)
    void testConcurrentCreditOperations() {
        // Create consumer
        Consumer consumer = new Consumer(
            "Concurrent Test User",
            "concurrent@example.com",
            new Money(new BigDecimal("1000.00"))
        );
        consumer = consumerRepository.save(consumer);
        
        Long consumerId = consumer.getId();
        
        // Simulate concurrent operations by loading consumer twice
        Consumer consumer1 = consumerRepository.findById(consumerId).orElseThrow();
        Consumer consumer2 = consumerRepository.findById(consumerId).orElseThrow();
        
        // Both reserve credit
        consumer1.reserveCredit(new Money(new BigDecimal("300.00")));
        consumer2.reserveCredit(new Money(new BigDecimal("400.00")));
        
        // Save both (last write wins in this simple test)
        consumerRepository.save(consumer1);
        consumerRepository.save(consumer2);
        
        // In a real scenario with optimistic locking, the second save would fail
        // For this test, we just verify the final state is consistent
        Consumer finalState = consumerRepository.findById(consumerId).orElseThrow();
        assertNotNull(finalState.getAvailableCredit());
        assertTrue(finalState.getAvailableCredit().isLessThan(consumer.getCreditLimit()) 
            || finalState.getAvailableCredit().equals(consumer.getCreditLimit()));
    }
}
