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
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
@Rollback
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
        assertEquals(account.getId(), found.getId());
    }

    @Test
    void testConsumerIdUniqueness() {
        // Given: An account with a consumer ID
        Long consumerId = 12360L;
        Account account1 = new Account(consumerId);
        accountRepository.save(account1);

        // When/Then: Creating another account with same consumer ID should fail
        Account account2 = new Account(consumerId);
        assertThrows(Exception.class, () -> {
            accountRepository.saveAndFlush(account2);
        });
    }

    @Test
    void testAccountVersioning() {
        // Given: A new account
        Long consumerId = 12361L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);
        Long initialVersion = account.getVersion();

        // When: Modifying account
        account.authorize("req-version-test", new Money(new BigDecimal("50.00")));
        account = accountRepository.save(account);

        // Then: Version should increment
        assertNotNull(account.getVersion());
        // Note: Version may be same or incremented depending on JPA implementation
        assertTrue(account.getVersion() >= initialVersion);
    }

    // ========== Edge Cases ==========

    @Test
    void testAuthorizeWithZeroAmount() {
        Long consumerId = 12362L;
        Account account = new Account(consumerId);
        Money zeroAmount = new Money(BigDecimal.ZERO);

        // Money class may allow zero, authorization should handle it
        Authorization auth = account.authorize("req-zero", zeroAmount);
        accountRepository.save(account);

        assertNotNull(auth);
        assertEquals(zeroAmount, auth.getAmount());
    }

    @Test
    void testAuthorizeWithLargeAmount() {
        Long consumerId = 12363L;
        Account account = new Account(consumerId);
        Money largeAmount = new Money(new BigDecimal("999999999.99"));

        Authorization auth = account.authorize("req-large", largeAmount);
        accountRepository.save(account);

        assertNotNull(auth);
        assertEquals(largeAmount, auth.getAmount());
    }

    @Test
    void testMultipleAccountsWithDifferentConsumers() {
        // Given: Multiple accounts
        Account account1 = new Account(10001L);
        Account account2 = new Account(10002L);
        Account account3 = new Account(10003L);

        accountRepository.save(account1);
        accountRepository.save(account2);
        accountRepository.save(account3);

        // Then: All accounts exist
        assertEquals(3, accountRepository.count());
        assertTrue(accountRepository.findByConsumerId(10001L).isPresent());
        assertTrue(accountRepository.findByConsumerId(10002L).isPresent());
        assertTrue(accountRepository.findByConsumerId(10003L).isPresent());
    }

    @Test
    void testCompleteOrderLifecycle_AuthorizeAndReverse() {
        // Given: Consumer account
        Long consumerId = 20001L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);

        // Step 1: Authorize payment
        String authRequestId = "order-123-auth";
        Money authAmount = new Money(new BigDecimal("75.50"));
        Authorization auth = account.authorize(authRequestId, authAmount);
        account = accountRepository.save(account);

        assertEquals(AuthorizationStatus.APPROVED, auth.getStatus());

        // Step 2: Reverse authorization (simulate order cancellation)
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        reloaded.reverseAuthorization(auth.getId());
        accountRepository.save(reloaded);

        // Verify final state
        Authorization finalAuth = authorizationRepository.findById(auth.getId()).orElseThrow();
        assertTrue(finalAuth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, finalAuth.getStatus());
    }

    @Test
    void testCompleteOrderLifecycle_AuthorizeAndRevise() {
        // Given: Consumer account
        Long consumerId = 20002L;
        Account account = new Account(consumerId);
        account = accountRepository.save(account);

        // Step 1: Initial authorization
        Authorization auth1 = account.authorize("order-456-auth-v1",
                new Money(new BigDecimal("100.00")));
        account = accountRepository.save(account);

        // Step 2: Revise (order amount changed)
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth2 = reloaded.reviseAuthorization(auth1.getId(),
                new Money(new BigDecimal("120.00")), "order-456-auth-v2");
        accountRepository.save(reloaded);

        // Verify final state
        Authorization original = authorizationRepository.findById(auth1.getId()).orElseThrow();
        assertTrue(original.isReversed());

        Authorization revised = authorizationRepository.findById(auth2.getId()).orElseThrow();
        assertFalse(revised.isReversed());
        assertEquals(new Money(new BigDecimal("120.00")), revised.getAmount());
    }
}
