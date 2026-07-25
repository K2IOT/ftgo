package net.ftgo.order.operations;

import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OrderOperationReconciler {

    private static final Logger logger = LoggerFactory.getLogger(OrderOperationReconciler.class);
    private static final int MAX_DETAILS_LENGTH = 1000;

    public enum Classification {
        RESUMABLE,
        COMPENSATABLE,
        COMPLETED,
        MANUAL_REVIEW
    }

    public enum RepairAction {
        RESTART,
        COMPENSATE,
        MARK_SUCCESS,
        FLAG_MANUAL
    }

    private final OrderRepository orderRepository;
    private final OrderOperationRepository operationRepository;
    private final SagaInstanceInspector sagaInspector;
    private final OrderRepairActionExecutor actionExecutor;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final OrderOperationTransactionRunner transactionRunner;

    @Autowired
    public OrderOperationReconciler(
        OrderRepository orderRepository,
        OrderOperationRepository operationRepository,
        SagaInstanceInspector sagaInspector,
        OrderRepairActionExecutor actionExecutor,
        MeterRegistry meterRegistry,
        OrderOperationTransactionRunner transactionRunner
    ) {
        this(
            orderRepository,
            operationRepository,
            sagaInspector,
            actionExecutor,
            meterRegistry,
            Clock.systemUTC(),
            transactionRunner
        );
    }

    public OrderOperationReconciler(
        OrderRepository orderRepository,
        OrderOperationRepository operationRepository,
        SagaInstanceInspector sagaInspector,
        OrderRepairActionExecutor actionExecutor,
        MeterRegistry meterRegistry,
        Clock clock
    ) {
        this(
            orderRepository,
            operationRepository,
            sagaInspector,
            actionExecutor,
            meterRegistry,
            clock,
            OrderOperationTransactionRunner.direct()
        );
    }

    public OrderOperationReconciler(
        OrderRepository orderRepository,
        OrderOperationRepository operationRepository,
        SagaInstanceInspector sagaInspector,
        OrderRepairActionExecutor actionExecutor,
        MeterRegistry meterRegistry,
        Clock clock,
        OrderOperationTransactionRunner transactionRunner
    ) {
        this.orderRepository = orderRepository;
        this.operationRepository = operationRepository;
        this.sagaInspector = sagaInspector;
        this.actionExecutor = actionExecutor;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.transactionRunner = transactionRunner;
    }

    public Assessment classify(Order order) {
        Optional<SagaInstanceInspector.SagaSnapshot> active =
            sagaInspector.findActiveForOrder(order.getId());
        OrderOperationType type = operationType(order.getState());
        Classification classification;
        String explanation;

        switch (order.getState()) {
            case APPROVAL_PENDING -> {
                if (active.isEmpty()) {
                    classification = Classification.RESUMABLE;
                    explanation = "Create saga is missing while order remains approval pending";
                } else if (active.get().failed()) {
                    classification = Classification.MANUAL_REVIEW;
                    explanation = "Create saga is failed; participant progress must be inspected";
                } else {
                    classification = Classification.RESUMABLE;
                    explanation = "Create saga exists and can continue from durable state";
                }
            }
            case CONFIRMATION_PENDING -> {
                if (hasDurableResourceIds(order)) {
                    classification = Classification.RESUMABLE;
                    explanation = "Confirmation saga can be reconstructed from durable resource IDs";
                } else {
                    classification = Classification.MANUAL_REVIEW;
                    explanation = "Confirmation state is missing one or more participant IDs";
                }
            }
            case REJECTION_PENDING -> {
                if (hasDurableResourceIds(order)) {
                    classification = Classification.COMPENSATABLE;
                    explanation = "Rejection compensation can be reconstructed from durable resource IDs";
                } else {
                    classification = Classification.MANUAL_REVIEW;
                    explanation = "Rejection state is missing financial or reservation IDs";
                }
            }
            case CANCEL_PENDING -> {
                if (active.isPresent() && !active.get().failed()) {
                    classification = Classification.RESUMABLE;
                    explanation = "Cancel saga remains active";
                } else {
                    classification = Classification.MANUAL_REVIEW;
                    explanation = "Cancel participant progress is ambiguous without an active saga";
                }
            }
            case REVISION_PENDING -> {
                classification = Classification.MANUAL_REVIEW;
                explanation = "Revision payment state and revised line items cannot be inferred safely";
            }
            case AWAITING_RESTAURANT_ACCEPTANCE, APPROVED, REJECTED, CANCELLED -> {
                classification = Classification.COMPLETED;
                explanation = "Order durable state no longer requires this saga operation";
            }
            default -> throw new IllegalStateException("Unsupported order state: " + order.getState());
        }

        long ageSeconds = order.getUpdatedAt() == null
            ? 0L
            : Math.max(0L, Duration.between(
                order.getUpdatedAt().toInstant(java.time.ZoneOffset.UTC),
                clock.instant()
            ).getSeconds());
        return new Assessment(
            order.getId(),
            type,
            classification,
            explanation,
            ageSeconds,
            active.orElse(null)
        );
    }

    public OrderOperation repair(
        Long orderId,
        RepairAction action,
        String reason,
        String idempotencyKey
    ) {
        requireText(reason, "reason");
        requireText(idempotencyKey, "idempotencyKey");

        PreparedRepair prepared;
        try {
            prepared = transactionRunner.required(
                () -> prepareRepair(orderId, action, reason, idempotencyKey)
            );
        } catch (DataIntegrityViolationException race) {
            return operationRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> race);
        }

        if (!prepared.execute()) {
            return prepared.operation();
        }

        try {
            return transactionRunner.required(
                () -> executeRepair(prepared.operation().getId(), orderId, action)
            );
        } catch (RuntimeException executionFailure) {
            try {
                transactionRunner.requiresNew(() -> {
                    recordFailure(prepared.operation().getId(), action, executionFailure);
                    return null;
                });
            } catch (RuntimeException auditFailure) {
                executionFailure.addSuppressed(auditFailure);
            }
            throw executionFailure;
        }
    }

    private PreparedRepair prepareRepair(
        Long orderId,
        RepairAction action,
        String reason,
        String idempotencyKey
    ) {
        Optional<OrderOperation> previous = operationRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            return new PreparedRepair(previous.get(), false);
        }

        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
        Assessment assessment = classify(order);
        validateAction(assessment, action);

        OrderOperation operation = new OrderOperation(
            orderId,
            assessment.operationType(),
            idempotencyKey,
            reason
        );
        operation.recordAssessment(
            assessment.classification().name(),
            action.name(),
            assessment.explanation()
        );
        return new PreparedRepair(operationRepository.saveAndFlush(operation), true);
    }

    private OrderOperation executeRepair(Long operationId, Long orderId, RepairAction action) {
        OrderOperation operation = operationRepository.findById(operationId)
            .orElseThrow(() -> new IllegalStateException(
                "Order repair operation not found: " + operationId
            ));
        if (operation.getStatus() != OrderOperationStatus.PENDING) {
            return operation;
        }

        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
        Assessment assessment = classify(order);
        validateAction(assessment, action);
        operation.recordAssessment(
            assessment.classification().name(),
            action.name(),
            assessment.explanation()
        );

        switch (action) {
            case RESTART -> {
                actionExecutor.restart(order, assessment.operationType());
                operation.markExecuted("Saga restart request was persisted");
            }
            case COMPENSATE -> {
                actionExecutor.compensate(order, assessment.operationType());
                operation.markExecuted("Compensation saga request was persisted");
            }
            case MARK_SUCCESS -> operation.markCompleted(
                "Durable order state already represents the completed outcome"
            );
            case FLAG_MANUAL -> operation.markManualReview(
                "Operator flagged operation for manual review"
            );
        }
        operationRepository.save(operation);
        meterRegistry.counter(
            "ftgo_order_reconciliation_action_total",
            "action", action.name(),
            "classification", assessment.classification().name()
        ).increment();
        logger.info(
            "Order repair executed: orderId={}, action={}, classification={}, idempotencyKey={}, reason={}",
            orderId,
            action,
            assessment.classification(),
            operation.getIdempotencyKey(),
            operation.getReason()
        );
        return operation;
    }

    private void recordFailure(
        Long operationId,
        RepairAction action,
        RuntimeException failure
    ) {
        OrderOperation operation = operationRepository.findById(operationId)
            .orElseThrow(() -> new IllegalStateException(
                "Order repair operation not found while recording failure: " + operationId
            ));
        operation.markFailed(failureDetails(failure));
        operationRepository.saveAndFlush(operation);
        meterRegistry.counter(
            "ftgo_order_reconciliation_failure_total",
            "action", action.name(),
            "exception", failure.getClass().getSimpleName()
        ).increment();
        logger.error(
            "Order repair failed: orderId={}, action={}, idempotencyKey={}",
            operation.getOrderId(),
            action,
            operation.getIdempotencyKey(),
            failure
        );
    }

    private String failureDetails(RuntimeException failure) {
        String message = failure.getMessage();
        String details = failure.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
        return details.length() <= MAX_DETAILS_LENGTH
            ? details
            : details.substring(0, MAX_DETAILS_LENGTH);
    }

    private void validateAction(Assessment assessment, RepairAction action) {
        boolean allowed = switch (action) {
            case RESTART -> assessment.classification() == Classification.RESUMABLE;
            case COMPENSATE -> assessment.classification() == Classification.COMPENSATABLE;
            case MARK_SUCCESS -> assessment.classification() == Classification.COMPLETED;
            case FLAG_MANUAL -> true;
        };
        if (!allowed) {
            throw new IllegalStateException(
                "Action " + action + " is not allowed for classification "
                    + assessment.classification()
            );
        }
    }

    private boolean hasDurableResourceIds(Order order) {
        return order.getTicketId() != null
            && order.getAuthorizationId() != null
            && order.getCreditReservationId() != null;
    }

    private OrderOperationType operationType(OrderState state) {
        return switch (state) {
            case APPROVAL_PENDING, AWAITING_RESTAURANT_ACCEPTANCE -> OrderOperationType.CREATE;
            case CONFIRMATION_PENDING -> OrderOperationType.CONFIRM;
            case REJECTION_PENDING, REJECTED -> OrderOperationType.REJECT;
            case CANCEL_PENDING, CANCELLED -> OrderOperationType.CANCEL;
            case REVISION_PENDING -> OrderOperationType.REVISE;
            case APPROVED -> OrderOperationType.CONFIRM;
        };
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private record PreparedRepair(OrderOperation operation, boolean execute) {
    }

    public record Assessment(
        Long orderId,
        OrderOperationType operationType,
        Classification classification,
        String explanation,
        long ageSeconds,
        SagaInstanceInspector.SagaSnapshot activeSaga
    ) {
    }
}
