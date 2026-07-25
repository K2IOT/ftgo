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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OrderOperationReconciler {

    private static final Logger logger = LoggerFactory.getLogger(OrderOperationReconciler.class);

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

    @Autowired
    public OrderOperationReconciler(
        OrderRepository orderRepository,
        OrderOperationRepository operationRepository,
        SagaInstanceInspector sagaInspector,
        OrderRepairActionExecutor actionExecutor,
        MeterRegistry meterRegistry
    ) {
        this(
            orderRepository,
            operationRepository,
            sagaInspector,
            actionExecutor,
            meterRegistry,
            Clock.systemUTC()
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
        this.orderRepository = orderRepository;
        this.operationRepository = operationRepository;
        this.sagaInspector = sagaInspector;
        this.actionExecutor = actionExecutor;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
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

    @Transactional
    public OrderOperation repair(
        Long orderId,
        RepairAction action,
        String reason,
        String idempotencyKey
    ) {
        requireText(reason, "reason");
        requireText(idempotencyKey, "idempotencyKey");

        Optional<OrderOperation> previous = operationRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            return previous.get();
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
        try {
            operationRepository.save(operation);
        } catch (DataIntegrityViolationException race) {
            return operationRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> race);
        }

        try {
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
                idempotencyKey,
                reason
            );
            return operation;
        } catch (RuntimeException e) {
            operation.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
            operationRepository.save(operation);
            meterRegistry.counter(
                "ftgo_order_reconciliation_failure_total",
                "action", action.name(),
                "exception", e.getClass().getSimpleName()
            ).increment();
            throw e;
        }
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
