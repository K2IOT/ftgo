package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.common.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Component
@Primary
public class RetryingSettlementGateway implements SettlementGateway {

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration);
    }

    private final SettlementGateway delegate;
    private final MeterRegistry meterRegistry;
    private final List<Duration> retryDelays;
    private final Sleeper sleeper;

    @Autowired
    public RetryingSettlementGateway(
        @Qualifier("simulatedSettlementGateway") SettlementGateway delegate,
        MeterRegistry meterRegistry,
        @Value("${ftgo.accounting.settlement.retry-delays-ms:1000,5000,30000}") String retryDelays
    ) {
        this(delegate, meterRegistry, parseDelays(retryDelays), RetryingSettlementGateway::sleepThread);
    }

    RetryingSettlementGateway(
        SettlementGateway delegate,
        MeterRegistry meterRegistry,
        List<Duration> retryDelays,
        Sleeper sleeper
    ) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        this.retryDelays = List.copyOf(retryDelays);
        this.sleeper = sleeper;
    }

    @Override
    public SettlementDecision authorize(Long authorizationId, Long orderId, Money amount, String requestId) {
        return execute("AUTHORIZE", () -> delegate.authorize(authorizationId, orderId, amount, requestId));
    }

    @Override
    public SettlementDecision capture(Long authorizationId, Long orderId, String requestId) {
        return execute("CAPTURE", () -> delegate.capture(authorizationId, orderId, requestId));
    }

    @Override
    public SettlementDecision voidAuthorization(Long authorizationId, Long orderId, String requestId) {
        return execute("VOID", () -> delegate.voidAuthorization(authorizationId, orderId, requestId));
    }

    @Override
    public SettlementDecision refund(Long authorizationId, Long orderId, Money amount, String requestId) {
        return execute("REFUND", () -> delegate.refund(authorizationId, orderId, amount, requestId));
    }

    @Override
    public Optional<ProviderSettlementSnapshot> find(Long authorizationId) {
        return delegate.find(authorizationId);
    }

    @Override
    public SettlementDecision synchronize(SettlementTarget target, String requestId) {
        return execute("SYNCHRONIZE", () -> delegate.synchronize(target, requestId));
    }

    private <T> T execute(String operation, Supplier<T> action) {
        int attempts = 0;
        SettlementGatewayTimeoutException lastTimeout = null;
        while (attempts <= retryDelays.size()) {
            attempts++;
            try {
                T result = action.get();
                meterRegistry.counter(
                    "ftgo.accounting.settlement.gateway.attempts",
                    "operation", operation,
                    "result", "success"
                ).increment();
                return result;
            } catch (SettlementGatewayTimeoutException timeout) {
                lastTimeout = timeout;
                meterRegistry.counter(
                    "ftgo.accounting.settlement.gateway.attempts",
                    "operation", operation,
                    "result", "timeout"
                ).increment();
                if (attempts > retryDelays.size()) break;
                sleeper.sleep(retryDelays.get(attempts - 1));
            }
        }
        meterRegistry.counter(
            "ftgo.accounting.settlement.gateway.retry.exhausted",
            "operation", operation
        ).increment();
        throw new SettlementRetryExhaustedException(operation, attempts, lastTimeout);
    }

    private static List<Duration> parseDelays(String configured) {
        if (configured == null || configured.isBlank()) return List.of();
        return Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(Long::parseLong)
            .peek(value -> {
                if (value < 0) throw new IllegalArgumentException("Retry delay cannot be negative");
            })
            .map(Duration::ofMillis)
            .toList();
    }

    private static void sleepThread(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SettlementRetryExhaustedException(
                "INTERRUPTED",
                1,
                new SettlementGatewayTimeoutException("Settlement retry interrupted")
            );
        }
    }
}