package net.ftgo.order.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.order.api.CreateOrderRequest;
import net.ftgo.order.api.OrderLineItemRequest;
import net.ftgo.order.api.ReviseOrderRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class OrderMutationIdempotencyService {

    public static final String CREATE_ORDER = "CREATE_ORDER";
    private static final Duration RECORD_TTL = Duration.ofHours(24);
    private static final int MAX_TRANSACTION_ATTEMPTS = 5;

    private final ApiIdempotencyStore store;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public OrderMutationIdempotencyService(
        ApiIdempotencyStore store,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this(store, objectMapper, transactionTemplate(transactionManager));
    }

    OrderMutationIdempotencyService(
        ApiIdempotencyStore store,
        ObjectMapper objectMapper
    ) {
        this(store, objectMapper, (TransactionTemplate) null);
    }

    private OrderMutationIdempotencyService(
        ApiIdempotencyStore store,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public IdempotentResult<String> execute(
        Long consumerId,
        String operation,
        String key,
        byte[] requestHash,
        Supplier<String> mutation
    ) {
        if (transactionTemplate == null) {
            return executeInTransaction(consumerId, operation, key, requestHash, mutation);
        }

        CannotAcquireLockException lastLockFailure = null;
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                return Objects.requireNonNull(transactionTemplate.execute(status ->
                    executeInTransaction(consumerId, operation, key, requestHash, mutation)
                ));
            } catch (CannotAcquireLockException lockFailure) {
                lastLockFailure = lockFailure;
            }
        }
        throw Objects.requireNonNull(lastLockFailure);
    }

    private IdempotentResult<String> executeInTransaction(
        Long consumerId,
        String operation,
        String key,
        byte[] requestHash,
        Supplier<String> mutation
    ) {
        Instant now = Instant.now();
        boolean inserted = store.insertProcessing(
            consumerId,
            operation,
            key,
            requestHash,
            now.plus(RECORD_TTL)
        );

        if (inserted) {
            return executeClaimOwnerMutation(
                consumerId,
                operation,
                key,
                mutation
            );
        }

        ApiIdempotencyRecord record = store.lock(consumerId, operation, key)
            .orElseThrow(() -> new IllegalStateException("Idempotency claim disappeared"));

        if (!MessageDigest.isEqual(record.requestHash(), requestHash)) {
            throw new IdempotencyKeyConflictException();
        }

        if (record.state() == ApiIdempotencyRecord.State.COMPLETED) {
            return new IdempotentResult<>(
                record.httpStatus(),
                record.responseJson(),
                record.resourceId(),
                true
            );
        }

        throw new IdempotencyRequestInProgressException();
    }

    private IdempotentResult<String> executeClaimOwnerMutation(
        Long consumerId,
        String operation,
        String key,
        Supplier<String> mutation
    ) {
        String responseJson = mutation.get();
        int httpStatus = CREATE_ORDER.equals(operation) ? 201 : 200;
        Long resourceId = extractResourceId(operation, responseJson);
        store.complete(
            consumerId,
            operation,
            key,
            httpStatus,
            responseJson,
            resourceId
        );
        return new IdempotentResult<>(httpStatus, responseJson, resourceId, false);
    }

    public byte[] hashCreate(Long consumerId, CreateOrderRequest request) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("consumerId", consumerId);
        canonical.put("operation", CREATE_ORDER);
        canonical.put("restaurantId", request.getRestaurantId());
        canonical.put("expectedMenuVersion", request.getExpectedMenuVersion());
        canonical.put("lineItems", canonicalLineItems(request.getLineItems()));
        canonical.put("deliveryAddress", canonicalAddress(request.getDeliveryAddress()));
        canonical.put("deliveryTime", request.getDeliveryTime().toString());
        return sha256(canonical);
    }

    public byte[] hashCancel(Long consumerId, Long orderId) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("consumerId", consumerId);
        canonical.put("operation", cancelOperation(orderId));
        canonical.put("orderId", orderId);
        return sha256(canonical);
    }

    public byte[] hashRevise(
        Long consumerId,
        Long orderId,
        ReviseOrderRequest request
    ) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("consumerId", consumerId);
        canonical.put("operation", reviseOperation(orderId));
        canonical.put("orderId", orderId);
        canonical.put("lineItems", canonicalLineItems(request.getRevisedLineItems()));
        return sha256(canonical);
    }

    public static String cancelOperation(Long orderId) {
        return "CANCEL_ORDER:" + orderId;
    }

    public static String reviseOperation(Long orderId) {
        return "REVISE_ORDER:" + orderId;
    }

    private List<Map<String, Object>> canonicalLineItems(
        List<OrderLineItemRequest> lineItems
    ) {
        List<Map<String, Object>> canonical = new ArrayList<>(lineItems.size());
        for (OrderLineItemRequest item : lineItems) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("menuItemId", item.getMenuItemId());
            value.put("name", item.getName());
            value.put("price", item.getPrice().getAmount().toPlainString());
            value.put("quantity", item.getQuantity());
            canonical.add(value);
        }
        return canonical;
    }

    private Map<String, Object> canonicalAddress(Address address) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("street", address.getStreet());
        canonical.put("city", address.getCity());
        canonical.put("state", address.getState());
        canonical.put("zipCode", address.getZipCode());
        return canonical;
    }

    private byte[] sha256(Map<String, Object> canonical) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(canonical)
                .getBytes(StandardCharsets.UTF_8);
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to canonicalize order mutation", error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private Long extractResourceId(String operation, String responseJson) {
        if (!CREATE_ORDER.equals(operation) || responseJson == null) {
            return null;
        }
        try {
            JsonNode response = objectMapper.readTree(responseJson);
            JsonNode orderId = response.get("orderId");
            if (orderId == null || !orderId.canConvertToLong()) {
                throw new IllegalStateException("Create order response has no orderId");
            }
            return orderId.longValue();
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Create order response is not valid JSON", error);
        }
    }

    private static TransactionTemplate transactionTemplate(
        PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }
}
