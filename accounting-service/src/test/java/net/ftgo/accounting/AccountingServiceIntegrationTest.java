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
 * <p>Spring Data JPA {@code save(...)} may merge a detached aggregate and return a managed copy.
 * Tests that need generated child identifiers therefore rebind to the returned aggregate instead of
 * retaining a transient child reference from the detached instance.
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
        authorizationRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Account saveAndFlush(Account account) {
        return accountRepository.saveAndFlush(account);
    }

    private Authorization requirePersistedAuthorization(Account account, String requestId) {
        Authorization authorization = account.findAuthorizationByRequestId(requestId);
        assertNotNull(authorization, "authorization must be present on the persisted aggregate");
        assertNotNull(authorization.getId(), "persisted authorization must have a generated id");
        return authorization;
    }

    @Test
    void testAuthorizationIdempotency_SameRequestIdReturnsSameResult() {
        Long consumerId = 12345L;
        Account account = saveAndFlush(new Account(consumerId));
        String requestId = "req-idempotent-001";
        Money amount = new Money(new BigDecimal("100.00"));

        account.authorize(requestId, amount);
        account = saveAndFlush(account);
        Authorization auth1 = requirePersistedAuthorization(account, requestId);

        assertEquals(requestId, auth1.getRequestId());
        assertEquals(amount, auth1.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, auth1.getStatus());

        Account reloadedAccount = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth2 = reloadedAccount.authorize(requestId, amount);

        assertNotNull(auth2);
        assertEquals(auth1.getId(), auth2.getId());
        assertEquals(auth1.getRequestId(), auth2.getRequestId());
        assertEquals(auth1.getAmount(), auth2.getAmount());
        assertEquals(1, authorizationRepository.findAll().size());
    }

    @Test
    void testAuthorizationIdempotency_MultipleCallsWithSameRequestId() {
        Long consumerId = 12346L;
        Account account = saveAndFlush(new Account(consumerId));
        String requestId = "req-multi-idempotent-001";
        Money amount = new Money(new BigDecimal("250.00"));

        account.authorize(requestId, amount);
        account = saveAndFlush(account);
        Authorization auth1 = requirePersistedAuthorization(account, requestId);

        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth2 = reloaded1.authorize(requestId, amount);
        saveAndFlush(reloaded1);

        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization auth3 = reloaded2.authorize(requestId, amount);
        saveAndFlush(reloaded2);

        assertEquals(auth1.getId(), auth2.getId());
        assertEquals(auth2.getId(), auth3.getId());
        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(1, allAuths.size());
        assertEquals(requestId, allAuths.get(0).getRequestId());
    }

    @Test
    void testAuthorizationIdempotency_DifferentRequestIdsCreateNewAuthorizations() {
        Long consumerId = 12347L;
        Account account = saveAndFlush(new Account(consumerId));
        Money amount = new Money(new BigDecimal("100.00"));

        account.authorize("req-001", amount);
        account = saveAndFlush(account);
        Authorization auth1 = requirePersistedAuthorization(account, "req-001");

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        reloaded.authorize("req-002", amount);
        reloaded = saveAndFlush(reloaded);
        Authorization auth2 = requirePersistedAuthorization(reloaded, "req-002");

        assertNotEquals(auth1.getId(), auth2.getId());
        assertNotEquals(auth1.getRequestId(), auth2.getRequestId());
        assertEquals(2, authorizationRepository.findAll().size());
    }

    @Test
    void testFindAuthorizationByRequestId() {
        Long consumerId = 12348L;
        Account account = new Account(consumerId);
        String requestId = "req-find-001";
        Money amount = new Money(new BigDecimal("150.00"));

        account.authorize(requestId, amount);
        saveAndFlush(account);

        Authorization found = authorizationRepository.findByRequestId(requestId).orElse(null);
        assertNotNull(found);
        assertEquals(requestId, found.getRequestId());
        assertEquals(amount, found.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, found.getStatus());
    }

    @Test
    void testReverseAuthorization() {
        Long consumerId = 12349L;
        Account account = new Account(consumerId);
        String requestId = "req-reverse-001";
        Money amount = new Money(new BigDecimal("200.00"));

        account.authorize(requestId, amount);
        account = saveAndFlush(account);
        Long authId = requirePersistedAuthorization(account, requestId).getId();

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        reloaded.reverseAuthorization(authId);
        saveAndFlush(reloaded);

        Authorization reversedAuth = authorizationRepository.findById(authId).orElseThrow();
        assertTrue(reversedAuth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, reversedAuth.getStatus());
        assertNotNull(reversedAuth.getReversedAt());
    }

    @Test
    void testReverseAuthorizationByRequestId() {
        Long consumerId = 12350L;
        Account account = new Account(consumerId);
        String requestId = "req-reverse-by-id-001";
        Money amount = new Money(new BigDecimal("175.00"));

        account.authorize(requestId, amount);
        account = saveAndFlush(account);

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        reloaded.reverseAuthorizationByRequestId(requestId);
        saveAndFlush(reloaded);

        Authorization reversedAuth = authorizationRepository.findByRequestId(requestId).orElseThrow();
        assertTrue(reversedAuth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, reversedAuth.getStatus());
        assertNotNull(reversedAuth.getReversedAt());
    }

    @Test
    void testReverseAuthorizationNotFound() {
        Account account = saveAndFlush(new Account(12351L));
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> reloaded.reverseAuthorization(999L));
    }

    @Test
    void testReverseAlreadyReversedAuthorization() {
        Account account = new Account(12352L);
        String requestId = "req-double-reverse-001";
        account.authorize(requestId, new Money(new BigDecimal("100.00")));
        account = saveAndFlush(account);
        Long authId = requirePersistedAuthorization(account, requestId).getId();

        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.reverseAuthorization(authId);
        saveAndFlush(reloaded1);

        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        assertThrows(IllegalStateException.class, () -> reloaded2.reverseAuthorization(authId));
    }

    @Test
    void testMultipleAuthorizationsWithSelectiveReversal() {
        Account account = new Account(12353L);
        account.authorize("req-001", new Money(new BigDecimal("100.00")));
        account.authorize("req-002", new Money(new BigDecimal("200.00")));
        account.authorize("req-003", new Money(new BigDecimal("150.00")));
        account = saveAndFlush(account);
        Long auth2Id = requirePersistedAuthorization(account, "req-002").getId();

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        reloaded.reverseAuthorization(auth2Id);
        saveAndFlush(reloaded);

        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(3, allAuths.size());
        assertEquals(1, allAuths.stream().filter(Authorization::isReversed).count());
        assertTrue(authorizationRepository.findById(auth2Id).orElseThrow().isReversed());
    }

    @Test
    void testReviseAuthorization() {
        Account account = new Account(12354L);
        String originalRequestId = "req-original-001";
        Money originalAmount = new Money(new BigDecimal("100.00"));
        account.authorize(originalRequestId, originalAmount);
        account = saveAndFlush(account);
        Long originalAuthId = requirePersistedAuthorization(account, originalRequestId).getId();

        Money newAmount = new Money(new BigDecimal("150.00"));
        String newRequestId = "req-revised-001";
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        Authorization newAuth = reloaded.reviseAuthorization(originalAuthId, newAmount, newRequestId);
        saveAndFlush(reloaded);

        Authorization originalFromDb = authorizationRepository.findById(originalAuthId).orElseThrow();
        assertTrue(originalFromDb.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, originalFromDb.getStatus());
        assertNotNull(newAuth);
        assertEquals(newRequestId, newAuth.getRequestId());
        assertEquals(newAmount, newAuth.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, newAuth.getStatus());
        assertFalse(newAuth.isReversed());
        assertEquals(2, authorizationRepository.findAll().size());
    }

    @Test
    void testReviseAuthorizationIdempotency() {
        Account account = new Account(12355L);
        String originalRequestId = "req-original-idempotent";
        account.authorize(originalRequestId, new Money(new BigDecimal("100.00")));
        account = saveAndFlush(account);
        Long originalAuthId = requirePersistedAuthorization(account, originalRequestId).getId();

        Money newAmount = new Money(new BigDecimal("200.00"));
        String newRequestId = "req-revised-idempotent";
        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.reviseAuthorization(originalAuthId, newAmount, newRequestId);
        reloaded1 = saveAndFlush(reloaded1);
        Authorization newAuth1 = requirePersistedAuthorization(reloaded1, newRequestId);

        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        Authorization newAuth2 = reloaded2.reviseAuthorization(originalAuthId, newAmount, newRequestId);
        saveAndFlush(reloaded2);

        assertEquals(newAuth1.getId(), newAuth2.getId());
        assertEquals(newAuth1.getRequestId(), newAuth2.getRequestId());
        assertEquals(2, authorizationRepository.findAll().size());
    }

    @Test
    void testReviseAuthorizationChain() {
        Account account = new Account(12356L);
        account.authorize("req-v1", new Money(new BigDecimal("100.00")));
        account = saveAndFlush(account);
        Long auth1Id = requirePersistedAuthorization(account, "req-v1").getId();

        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.reviseAuthorization(auth1Id, new Money(new BigDecimal("150.00")), "req-v2");
        reloaded1 = saveAndFlush(reloaded1);
        Long auth2Id = requirePersistedAuthorization(reloaded1, "req-v2").getId();

        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded2.reviseAuthorization(auth2Id, new Money(new BigDecimal("200.00")), "req-v3");
        reloaded2 = saveAndFlush(reloaded2);
        Long auth3Id = requirePersistedAuthorization(reloaded2, "req-v3").getId();

        Authorization auth1FromDb = authorizationRepository.findById(auth1Id).orElseThrow();
        Authorization auth2FromDb = authorizationRepository.findById(auth2Id).orElseThrow();
        Authorization auth3FromDb = authorizationRepository.findById(auth3Id).orElseThrow();
        assertTrue(auth1FromDb.isReversed(), "Original should be reversed");
        assertTrue(auth2FromDb.isReversed(), "First revision should be reversed");
        assertFalse(auth3FromDb.isReversed(), "Latest revision should be active");
        assertEquals(new Money(new BigDecimal("200.00")), auth3FromDb.getAmount());
        assertEquals(3, authorizationRepository.findAll().size());
    }

    @Test
    void testReviseAuthorizationNotFound() {
        Account account = saveAndFlush(new Account(12357L));
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> reloaded.reviseAuthorization(
                999L, new Money(new BigDecimal("150.00")), "req-new"));
    }

    @Test
    void testReviseReversedAuthorization() {
        Account account = new Account(12358L);
        String requestId = "req-reversed";
        account.authorize(requestId, new Money(new BigDecimal("100.00")));
        account = saveAndFlush(account);
        Long authId = requirePersistedAuthorization(account, requestId).getId();

        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.reverseAuthorization(authId);
        saveAndFlush(reloaded1);

        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        assertThrows(IllegalStateException.class, () -> reloaded2.reviseAuthorization(
                authId, new Money(new BigDecimal("150.00")), "req-new"));
    }

    @Test
    void testCreateAndFindAccountByConsumerId() {
        Long consumerId = 12359L;
        Account account = saveAndFlush(new Account(consumerId));
        Account found = accountRepository.findByConsumerId(consumerId).orElse(null);
        assertNotNull(found);
        assertEquals(consumerId, found.getConsumerId());
        assertNotNull(found.getCreatedAt());
        assertEquals(account.getId(), found.getId());
    }

    @Test
    void testAccountWithMultipleAuthorizations() {
        Account account = new Account(12360L);
        account.authorize("req-001", new Money(new BigDecimal("100.00")));
        account.authorize("req-002", new Money(new BigDecimal("200.00")));
        account.authorize("req-003", new Money(new BigDecimal("150.00")));
        account = saveAndFlush(account);

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(3, reloaded.getAuthorizations().size());
        assertEquals(3, authorizationRepository.findAll().size());
    }

    @Test
    void testExistsByConsumerId() {
        Long consumerId = 12361L;
        saveAndFlush(new Account(consumerId));
        assertTrue(accountRepository.existsByConsumerId(consumerId));
        assertFalse(accountRepository.existsByConsumerId(99999L));
    }

    @Test
    void testCompleteOrderLifecycle_AuthorizeAndReverse() {
        Account account = saveAndFlush(new Account(12362L));
        String authRequestId = "order-123-auth";
        Money orderAmount = new Money(new BigDecimal("75.50"));

        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.authorize(authRequestId, orderAmount);
        reloaded1 = saveAndFlush(reloaded1);
        Long authId = requirePersistedAuthorization(reloaded1, authRequestId).getId();

        Authorization authFromDb = authorizationRepository.findById(authId).orElseThrow();
        assertEquals(AuthorizationStatus.APPROVED, authFromDb.getStatus());
        assertFalse(authFromDb.isReversed());

        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded2.reverseAuthorization(authId);
        saveAndFlush(reloaded2);

        Authorization reversedFromDb = authorizationRepository.findById(authId).orElseThrow();
        assertEquals(AuthorizationStatus.REVERSED, reversedFromDb.getStatus());
        assertTrue(reversedFromDb.isReversed());
        assertNotNull(reversedFromDb.getReversedAt());
    }

    @Test
    void testCompleteOrderLifecycle_AuthorizeAndRevise() {
        Account account = saveAndFlush(new Account(12363L));
        String originalAuthRequestId = "order-456-auth";
        Money originalAmount = new Money(new BigDecimal("50.00"));

        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.authorize(originalAuthRequestId, originalAmount);
        reloaded1 = saveAndFlush(reloaded1);
        Long originalAuthId = requirePersistedAuthorization(reloaded1, originalAuthRequestId).getId();

        assertEquals(
                AuthorizationStatus.APPROVED,
                authorizationRepository.findById(originalAuthId).orElseThrow().getStatus());

        String revisedAuthRequestId = "order-456-auth-revised";
        Money revisedAmount = new Money(new BigDecimal("85.00"));
        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded2.reviseAuthorization(originalAuthId, revisedAmount, revisedAuthRequestId);
        reloaded2 = saveAndFlush(reloaded2);
        Long revisedAuthId = requirePersistedAuthorization(reloaded2, revisedAuthRequestId).getId();

        assertEquals(
                AuthorizationStatus.REVERSED,
                authorizationRepository.findById(originalAuthId).orElseThrow().getStatus());
        Authorization revisedFromDb = authorizationRepository.findById(revisedAuthId).orElseThrow();
        assertEquals(AuthorizationStatus.APPROVED, revisedFromDb.getStatus());
        assertEquals(revisedAmount, revisedFromDb.getAmount());
        assertFalse(revisedFromDb.isReversed());
    }

    @Test
    void testConcurrentAuthorizationsForDifferentOrders() {
        Account account = saveAndFlush(new Account(12364L));

        Account reloaded1 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded1.authorize("order-100-auth", new Money(new BigDecimal("25.00")));
        saveAndFlush(reloaded1);

        Account reloaded2 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded2.authorize("order-101-auth", new Money(new BigDecimal("40.00")));
        saveAndFlush(reloaded2);

        Account reloaded3 = accountRepository.findById(account.getId()).orElseThrow();
        reloaded3.authorize("order-102-auth", new Money(new BigDecimal("60.00")));
        saveAndFlush(reloaded3);

        List<Authorization> allAuths = authorizationRepository.findAll();
        assertEquals(3, allAuths.size());
        assertEquals(3, allAuths.stream().filter(Authorization::isApproved).count());
    }
}
