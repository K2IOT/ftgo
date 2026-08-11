package net.ftgo.accounting;

import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Accounting Service with Testcontainers.
 * 
 * Tests the complete stack including:
 * - MySQL database persistence
 * - Kafka messaging infrastructure
 * - JPA repositories
 * - Domain logic with database transactions
 * 
 * Validates Requirements 7: Payment Authorization
 */
@SpringBootTest
@Testcontainers
class AccountingServiceIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("ftgo_accounting_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withReuse(true);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private AuthorizationRepository authorizationRepository;
    
    @BeforeEach
    void setUp() {
        // Clean up database before each test
        authorizationRepository.deleteAll();
        accountRepository.deleteAll();
    }
    
    // ========== Authorization Idempotency Tests (Requirement 7.5, 7.7) ==========
    
    @Test
    void testAuthorizationIdempotency_SameRequestIdReturnsSameResult() {
        // Given: An account with an authorization
        Long consumerId = 12345L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        String requestId = "req-idempotent-001";
        Money amount = new Money(new BigDecimal("100.00"));
        
        // When: First authorization
        Authorization auth1 = account.authorize(requestId, amount);
        account = accountRepository.save(account);
        
        // Then: Authorization is created
        assertNotNull(auth1);
        assertEquals(requestId, auth1.getRequestId());
        assertEquals(amount, auth1.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, auth1.getStatus());
        
        // When: Second authorization with same requestId (reload account from DB)
        Account reloadedAccount = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth2 = reloadedAccount.authorize(requestId, amount);
        
        // Then: Same authorization is returned (idempotency)
        assertNotNull(auth2);
        assertEquals(auth1.getId(), auth2.getId());
        assertEquals(auth1.getRequestId(), auth2.getRequestId());
        assertEquals(auth1.getAmount(), auth2.getAmount());
        
        // Verify only one authorization exists in database
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(1, allAuths.size());
    }
    
    @Test
    void testAuthorizationIdempotency_MultipleCallsWithSameRequestId() {
        // Given: An account
        Long consumerId = 12346L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        String requestId = "req-multi-idempotent-001";
        Money amount = new Money(new BigDecimal("250.00"));
        
        // When: Multiple authorizations with same requestId
        Authorization auth1 = account.authorize(requestId, amount);
        account = accountRepository.save(account);
        
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth2 = reloaded1.authorize(requestId, amount);
        accountRepository.save(reloaded1);
        
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth3 = reloaded2.authorize(requestId, amount);
        accountRepository.save(reloaded2);
        
        // Then: All return the same authorization
        assertEquals(auth1.getId(), auth2.getId());
        assertEquals(auth2.getId(), auth3.getId());
        
        // Verify only one authorization exists in database
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(1, allAuths.size());
        assertEquals(requestId, allAuths.get(0).getRequestId());
    }
    
    @Test
    void testAuthorizationIdempotency_DifferentRequestIdsCreateNewAuthorizations() {
        // Given: An account
        Long consumerId = 12347L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        Money amount = new Money(new BigDecimal("100.00"));
        
        // When: Multiple authorizations with different requestIds
        Authorization auth1 = account.authorize("req-001", amount);
        account = accountRepository.save(account);
        
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth2 = reloaded.authorize("req-002", amount);
        accountRepository.save(reloaded);
        
        // Then: Different authorizations are created
        assertNotEquals(auth1.getId(), auth2.getId());
        assertNotEquals(auth1.getRequestId(), auth2.getRequestId());
        
        // Verify two authorizations exist in database
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(2, allAuths.size());
    }
    
    @Test
    void testFindAuthorizationByRequestId() {
        // Given: An account with an authorization
        Long consumerId = 12348L;
        Account account = new Account(consumerId);
        String requestId = "req-find-001";
        Money amount = new Money(new BigDecimal("150.00"));
        
        Authorization auth = account.authorize(requestId, amount);
        account = accountRepository.save(account);
        
        // When: Finding authorization by requestId
        Authorization found = authorizationRepository.findByRequestId(requestId).orElse(null);
        
        // Then: Authorization is found
        assertNotNull(found);
        assertEquals(requestId, found.getRequestId());
        assertEquals(amount, found.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, found.getStatus());
    }
    
    // ========== Authorization Reversal Tests (Requirement 7.3) ==========
    
    @Test
    void testReverseAuthorization() {
        // Given: An account with an authorization
        Long consumerId = 12349L;
        Account account = new Account(consumerId);
        String requestId = "req-reverse-001";
        Money amount = new Money(new BigDecimal("200.00"));
        
        Authorization auth = account.authorize(requestId, amount);
        account = accountRepository.save(account);
        Long authId = auth.getId();
        
        // When: Reversing the authorization
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        reloaded.reverseAuthorization(authId);
        accountRepository.save(reloaded);
        
        // Then: Authorization is reversed in database
        Authorization reversedAuth = authorizationRepository.findById(authId).orElseThrow();
        assertTrue(reversedAuth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, reversedAuth.getStatus());
        assertNotNull(reversedAuth.getReversedAt());
    }
    
    @Test
    void testReverseAuthorizationByRequestId() {
        // Given: An account with an authorization
        Long consumerId = 12350L;
        Account account = new Account(consumerId);
        String requestId = "req-reverse-by-id-001";
        Money amount = new Money(new BigDecimal("175.00"));
        
        Authorization auth = account.authorize(requestId, amount);
        account = accountRepository.save(account);
        
        // When: Reversing by request ID
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        reloaded.reverseAuthorizationByRequestId(requestId);
        accountRepository.save(reloaded);
        
        // Then: Authorization is reversed in database
        Authorization reversedAuth = authorizationRepository.findByRequestId(requestId).orElseThrow();
        assertTrue(reversedAuth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, reversedAuth.getStatus());
        assertNotNull(reversedAuth.getReversedAt());
    }
    
    @Test
    void testReverseAuthorizationNotFound() {
        // Given: An account without authorizations
        Long consumerId = 12351L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        // When/Then: Reversing non-existent authorization throws exception
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> {
            reloaded.reverseAuthorization(999L);
        });
    }
    
    @Test
    void testReverseAlreadyReversedAuthorization() {
        // Given: An account with a reversed authorization
        Long consumerId = 12352L;
        Account account = new Account(consumerId);
        String requestId = "req-double-reverse-001";
        Money amount = new Money(new BigDecimal("100.00"));
        
        Authorization auth = account.authorize(requestId, amount);
        account = accountRepository.save(account);
        Long authId = auth.getId();
        
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.reverseAuthorization(authId);
        accountRepository.save(reloaded1);
        
        // When/Then: Reversing again throws exception
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        assertThrows(IllegalStateException.class, () -> {
            reloaded2.reverseAuthorization(authId);
        });
    }
    
    @Test
    void testMultipleAuthorizationsWithSelectiveReversal() {
        // Given: An account with multiple authorizations
        Long consumerId = 12353L;
        Account account = new Account(consumerId);
        
        Authorization auth1 = account.authorize("req-001", new Money(new BigDecimal("100.00")));
        Authorization auth2 = account.authorize("req-002", new Money(new BigDecimal("200.00")));
        Authorization auth3 = account.authorize("req-003", new Money(new BigDecimal("150.00")));
        account = accountRepository.save(account);
        
        Long auth2Id = auth2.getId();
        
        // When: Reversing only one authorization
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        reloaded.reverseAuthorization(auth2Id);
        accountRepository.save(reloaded);
        
        // Then: Only the specified authorization is reversed
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(3, allAuths.size());
        
        long reversedCount = allAuths.stream()
                .filter(Authorization::isReversed)
                .count();
        assertEquals(1, reversedCount);
        
        Authorization reversedAuth = authorizationRepository.findById(auth2Id).orElseThrow();
        assertTrue(reversedAuth.isReversed());
    }
    
    // ========== Authorization Revision Tests (Requirement 7.4) ==========
    
    @Test
    void testReviseAuthorization() {
        // Given: An account with an authorization
        Long consumerId = 12354L;
        Account account = new Account(consumerId);
        String originalRequestId = "req-original-001";
        Money originalAmount = new Money(new BigDecimal("100.00"));
        
        Authorization originalAuth = account.authorize(originalRequestId, originalAmount);
        account = accountRepository.save(account);
        Long originalAuthId = originalAuth.getId();
        
        // When: Revising the authorization
        Money newAmount = new Money(new BigDecimal("150.00"));
        String newRequestId = "req-revised-001";
        
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        Authorization newAuth = reloaded.reviseAuthorization(originalAuthId, newAmount, newRequestId);
        accountRepository.save(reloaded);
        
        // Then: Original authorization is reversed
        Authorization originalFromDb = authorizationRepository.findById(originalAuthId).orElseThrow();
        assertTrue(originalFromDb.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, originalFromDb.getStatus());
        
        // And: New authorization is created
        assertNotNull(newAuth);
        assertEquals(newRequestId, newAuth.getRequestId());
        assertEquals(newAmount, newAuth.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, newAuth.getStatus());
        assertFalse(newAuth.isReversed());
        
        // And: Both authorizations exist in database
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(2, allAuths.size());
    }
    
    @Test
    void testReviseAuthorizationIdempotency() {
        // Given: An account with an authorization
        Long consumerId = 12355L;
        Account account = new Account(consumerId);
        String originalRequestId = "req-original-idempotent";
        Money originalAmount = new Money(new BigDecimal("100.00"));
        
        Authorization originalAuth = account.authorize(originalRequestId, originalAmount);
        account = accountRepository.save(account);
        Long originalAuthId = originalAuth.getId();
        
        Money newAmount = new Money(new BigDecimal("200.00"));
        String newRequestId = "req-revised-idempotent";
        
        // When: First revision
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization newAuth1 = reloaded1.reviseAuthorization(originalAuthId, newAmount, newRequestId);
        accountRepository.save(reloaded1);
        
        // When: Second revision with same newRequestId (idempotency check)
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization newAuth2 = reloaded2.reviseAuthorization(originalAuthId, newAmount, newRequestId);
        accountRepository.save(reloaded2);
        
        // Then: Same new authorization is returned
        assertEquals(newAuth1.getId(), newAuth2.getId());
        assertEquals(newAuth1.getRequestId(), newAuth2.getRequestId());
        
        // And: Only 2 authorizations exist (original + one new)
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(2, allAuths.size());
    }
    
    @Test
    void testReviseAuthorizationChain() {
        // Given: An account with an authorization
        Long consumerId = 12356L;
        Account account = new Account(consumerId);
        
        Authorization auth1 = account.authorize("req-v1", new Money(new BigDecimal("100.00")));
        account = accountRepository.save(account);
        Long auth1Id = auth1.getId();
        
        // When: First revision
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth2 = reloaded1.reviseAuthorization(auth1Id, 
                new Money(new BigDecimal("150.00")), "req-v2");
        accountRepository.save(reloaded1);
        Long auth2Id = auth2.getId();
        
        // When: Second revision
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth3 = reloaded2.reviseAuthorization(auth2Id, 
                new Money(new BigDecimal("200.00")), "req-v3");
        accountRepository.save(reloaded2);
        
        // Then: Verify chain in database
        Authorization auth1FromDb = authorizationRepository.findById(auth1Id).orElseThrow();
        Authorization auth2FromDb = authorizationRepository.findById(auth2Id).orElseThrow();
        Authorization auth3FromDb = authorizationRepository.findById(auth3.getId()).orElseThrow();
        
        assertTrue(auth1FromDb.isReversed(), "Original should be reversed");
        assertTrue(auth2FromDb.isReversed(), "First revision should be reversed");
        assertFalse(auth3FromDb.isReversed(), "Latest revision should be active");
        
        assertEquals(new Money(new BigDecimal("200.00")), auth3FromDb.getAmount());
        
        // And: All 3 authorizations exist in database
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(3, allAuths.size());
    }
    
    @Test
    void testReviseAuthorizationNotFound() {
        // Given: An account without authorizations
        Long consumerId = 12357L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        // When/Then: Revising non-existent authorization throws exception
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> {
            reloaded.reviseAuthorization(999L, 
                    new Money(new BigDecimal("150.00")), "req-new");
        });
    }
    
    @Test
    void testReviseReversedAuthorization() {
        // Given: An account with a reversed authorization
        Long consumerId = 12358L;
        Account account = new Account(consumerId);
        String requestId = "req-reversed";
        Money amount = new Money(new BigDecimal("100.00"));
        
        Authorization auth = account.authorize(requestId, amount);
        account = accountRepository.save(account);
        Long authId = auth.getId();
        
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.reverseAuthorization(authId);
        accountRepository.save(reloaded1);
        
        // When/Then: Revising reversed authorization throws exception
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        assertThrows(IllegalStateException.class, () -> {
            reloaded2.reviseAuthorization(authId, 
                    new Money(new BigDecimal("150.00")), "req-new");
        });
    }
    
    // ========== Account Management Tests ==========
    
    @Test
    void testCreateAndFindAccountByConsumerId() {
        // Given: A new account
        Long consumerId = 12359L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        // When: Finding by consumer ID
        Account found = accountRepository.findByConsumerId(consumerId).orElse(null);
        
        // Then: Account is found
        assertNotNull(found);
        assertEquals(consumerId, found.getConsumerId());
        assertNotNull(found.getCreatedAt());
    }
    
    @Test
    void testAccountWithMultipleAuthorizations() {
        // Given: An account with multiple authorizations
        Long consumerId = 12360L;
        Account account = new Account(consumerId);
        
        account.authorize("req-001", new Money(new BigDecimal("100.00")));
        account.authorize("req-002", new Money(new BigDecimal("200.00")));
        account.authorize("req-003", new Money(new BigDecimal("150.00")));
        
        account = accountRepository.save(account);
        
        // When: Reloading account
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        
        // Then: All authorizations are loaded
        assertEquals(3, reloaded.getAuthorizations().size());
        
        // And: All authorizations are persisted
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(3, allAuths.size());
    }
    
    @Test
    void testExistsByConsumerId() {
        // Given: An account
        Long consumerId = 12361L;
        Account account = new Account(consumerId);
        accountRepository.save(account);
        
        // When/Then: Checking existence
        assertTrue(accountRepository.existsByConsumerId(consumerId));
        assertFalse(accountRepository.existsByConsumerId(99999L));
    }
    
    // ========== Complex Integration Scenarios ==========
    
    @Test
    void testCompleteOrderLifecycle_AuthorizeAndReverse() {
        // Simulates CreateOrderSaga followed by CancelOrderSaga
        
        // Given: An account
        Long consumerId = 12362L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        // When: Authorizing for order (CreateOrderSaga)
        String authRequestId = "order-123-auth";
        Money orderAmount = new Money(new BigDecimal("75.50"));
        
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth = reloaded1.authorize(authRequestId, orderAmount);
        accountRepository.save(reloaded1);
        Long authId = auth.getId();
        
        // Then: Authorization is approved
        Authorization authFromDb = authorizationRepository.findById(authId).orElseThrow();
        assertEquals(AuthorizationStatus.APPROVED, authFromDb.getStatus());
        assertFalse(authFromDb.isReversed());
        
        // When: Reversing authorization (CancelOrderSaga)
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded2.reverseAuthorization(authId);
        accountRepository.save(reloaded2);
        
        // Then: Authorization is reversed
        Authorization reversedFromDb = authorizationRepository.findById(authId).orElseThrow();
        assertEquals(AuthorizationStatus.REVERSED, reversedFromDb.getStatus());
        assertTrue(reversedFromDb.isReversed());
        assertNotNull(reversedFromDb.getReversedAt());
    }
    
    @Test
    void testCompleteOrderLifecycle_AuthorizeAndRevise() {
        // Simulates CreateOrderSaga followed by ReviseOrderSaga
        
        // Given: An account
        Long consumerId = 12363L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        // When: Authorizing for order (CreateOrderSaga)
        String originalAuthRequestId = "order-456-auth";
        Money originalAmount = new Money(new BigDecimal("50.00"));
        
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization originalAuth = reloaded1.authorize(originalAuthRequestId, originalAmount);
        accountRepository.save(reloaded1);
        Long originalAuthId = originalAuth.getId();
        
        // Then: Original authorization is approved
        Authorization originalFromDb = authorizationRepository.findById(originalAuthId).orElseThrow();
        assertEquals(AuthorizationStatus.APPROVED, originalFromDb.getStatus());
        
        // When: Revising authorization (ReviseOrderSaga - order total changed)
        String revisedAuthRequestId = "order-456-auth-revised";
        Money revisedAmount = new Money(new BigDecimal("85.00"));
        
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization revisedAuth = reloaded2.reviseAuthorization(
                originalAuthId, revisedAmount, revisedAuthRequestId);
        accountRepository.save(reloaded2);
        
        // Then: Original authorization is reversed
        Authorization originalAfterRevision = authorizationRepository.findById(originalAuthId).orElseThrow();
        assertEquals(AuthorizationStatus.REVERSED, originalAfterRevision.getStatus());
        
        // And: New authorization is approved with new amount
        Authorization revisedFromDb = authorizationRepository.findById(revisedAuth.getId()).orElseThrow();
        assertEquals(AuthorizationStatus.APPROVED, revisedFromDb.getStatus());
        assertEquals(revisedAmount, revisedFromDb.getAmount());
        assertFalse(revisedFromDb.isReversed());
    }
    
    @Test
    void testConcurrentAuthorizationsForDifferentOrders() {
        // Simulates multiple concurrent orders from same consumer
        
        // Given: An account
        Long consumerId = 12364L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        
        // When: Multiple authorizations for different orders
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth1 = reloaded1.authorize("order-100-auth", 
                new Money(new BigDecimal("25.00")));
        accountRepository.save(reloaded1);
        
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth2 = reloaded2.authorize("order-101-auth", 
                new Money(new BigDecimal("40.00")));
        accountRepository.save(reloaded2);
        
        Account reloaded3 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth3 = reloaded3.authorize("order-102-auth", 
                new Money(new BigDecimal("60.00")));
        accountRepository.save(reloaded3);
        
        // Then: All authorizations are approved
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(3, allAuths.size());
        
        long approvedCount = allAuths.stream()
                .filter(Authorization::isApproved)
                .count();
        assertEquals(3, approvedCount);
    }
}
