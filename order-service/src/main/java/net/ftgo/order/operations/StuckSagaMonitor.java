package net.ftgo.order.operations;

import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StuckSagaMonitor {

    private static final EnumSet<OrderState> MONITORED_STATES = EnumSet.of(
        OrderState.APPROVAL_PENDING,
        OrderState.CONFIRMATION_PENDING,
        OrderState.REJECTION_PENDING,
        OrderState.CANCEL_PENDING,
        OrderState.REVISION_PENDING
    );

    private final OrderRepository orderRepository;
    private final OrderOperationReconciler reconciler;
    private final MeterRegistry meterRegistry;
    private final Duration createThreshold;
    private final Duration mutationThreshold;
    private final Clock clock;
    private final AtomicInteger stuckCount = new AtomicInteger();

    @Autowired
    public StuckSagaMonitor(
        OrderRepository orderRepository,
        OrderOperationReconciler reconciler,
        MeterRegistry meterRegistry,
        @Value("${ftgo.operations.create-stuck-threshold:PT5M}") Duration createThreshold,
        @Value("${ftgo.operations.mutation-stuck-threshold:PT10M}") Duration mutationThreshold
    ) {
        this(
            orderRepository,
            reconciler,
            meterRegistry,
            createThreshold,
            mutationThreshold,
            Clock.systemUTC()
        );
    }

    StuckSagaMonitor(
        OrderRepository orderRepository,
        OrderOperationReconciler reconciler,
        MeterRegistry meterRegistry,
        Duration createThreshold,
        Duration mutationThreshold,
        Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.reconciler = reconciler;
        this.meterRegistry = meterRegistry;
        this.createThreshold = createThreshold;
        this.mutationThreshold = mutationThreshold;
        this.clock = clock;
        meterRegistry.gauge("ftgo_stuck_saga_count", stuckCount);
    }

    @Scheduled(fixedDelayString = "${ftgo.operations.stuck-scan-ms:60000}")
    public void scan() {
        inspectNow();
    }

    public List<OrderOperationReconciler.Assessment> inspectNow() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime broadCutoff = now.minus(
            createThreshold.compareTo(mutationThreshold) <= 0
                ? createThreshold
                : mutationThreshold
        );
        List<OrderOperationReconciler.Assessment> assessments = orderRepository
            .findByStateInAndUpdatedAtBefore(MONITORED_STATES, broadCutoff)
            .stream()
            .filter(order -> isPastThreshold(order, now))
            .map(reconciler::classify)
            .toList();

        stuckCount.set(assessments.size());
        assessments.forEach(assessment -> meterRegistry.counter(
            "ftgo_stuck_saga_detected_total",
            "operation", assessment.operationType().name(),
            "classification", assessment.classification().name()
        ).increment());
        return assessments;
    }

    private boolean isPastThreshold(Order order, LocalDateTime now) {
        if (order.getUpdatedAt() == null) {
            return false;
        }
        Duration threshold = order.getState() == OrderState.APPROVAL_PENDING
            ? createThreshold
            : mutationThreshold;
        return !order.getUpdatedAt().isAfter(now.minus(threshold));
    }
}
