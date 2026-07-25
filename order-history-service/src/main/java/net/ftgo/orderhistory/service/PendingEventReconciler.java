package net.ftgo.orderhistory.service;

import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.common.messaging.KafkaDeadLetterSupport;
import net.ftgo.orderhistory.domain.PendingOrderEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class PendingEventReconciler {

    public static final String PROJECTION_DLT = "net.ftgo.orderhistory.projection.DLT";

    private final PendingOrderEventStore pendingEventStore;
    private final OrderHistoryProjectionService projectionService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public PendingEventReconciler(
        PendingOrderEventStore pendingEventStore,
        OrderHistoryProjectionService projectionService,
        KafkaTemplate<Object, Object> kafkaTemplate,
        MeterRegistry meterRegistry
    ) {
        this.pendingEventStore = pendingEventStore;
        this.projectionService = projectionService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${ftgo.order-history.pending-reconcile-ms:30000}")
    public void reconcileDueEvents() {
        Instant now = Instant.now();
        for (PendingOrderEvent pending : pendingEventStore.findDue(now, 100)) {
            if (pending.isTerminal(now)) {
                publishTerminalFailure(pending, "terminal retry policy reached");
                pendingEventStore.delete(pending);
                continue;
            }
            try {
                OrderHistoryProjectionService.ProjectionResult result =
                    projectionService.reconcile(pending);
                if (result == OrderHistoryProjectionService.ProjectionResult.PENDING) {
                    pending.recordRetry("projection prerequisite still missing", now);
                    pendingEventStore.save(pending);
                }
            } catch (RuntimeException e) {
                pending.recordRetry(e.getClass().getSimpleName() + ": " + e.getMessage(), now);
                if (pending.isTerminal(now)) {
                    publishTerminalFailure(pending, pending.getLastError());
                    pendingEventStore.delete(pending);
                } else {
                    pendingEventStore.save(pending);
                }
            }
        }
    }

    private void publishTerminalFailure(PendingOrderEvent pending, String reason) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(
            KafkaDeadLetterSupport.DLT_EVENT_ID,
            pending.getKey().getEventId().toString().getBytes(StandardCharsets.UTF_8)
        );
        headers.add(
            KafkaDeadLetterSupport.DLT_EXCEPTION_CLASS,
            "ProjectionTerminalFailure".getBytes(StandardCharsets.UTF_8)
        );
        headers.add(
            "ftgo_dlt_failure_reason",
            String.valueOf(reason).getBytes(StandardCharsets.UTF_8)
        );
        kafkaTemplate.send(new ProducerRecord<>(
            PROJECTION_DLT,
            null,
            pending.getKey().getOrderId(),
            pending.getEnvelope(),
            headers
        ));
        meterRegistry.counter(
            "ftgo_order_history_pending_terminal_total",
            "event_type", pending.getEventType()
        ).increment();
    }
}
