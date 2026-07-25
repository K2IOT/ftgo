package net.ftgo.order.api.admin;

import net.ftgo.order.operations.OrderOperation;
import net.ftgo.order.operations.OrderOperationReconciler;
import net.ftgo.order.operations.OrderOperationRepository;
import net.ftgo.order.operations.StuckSagaMonitor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/saga-operations")
public class SagaOperationsController {

    private final StuckSagaMonitor monitor;
    private final OrderOperationReconciler reconciler;
    private final OrderOperationRepository operationRepository;

    public SagaOperationsController(
        StuckSagaMonitor monitor,
        OrderOperationReconciler reconciler,
        OrderOperationRepository operationRepository
    ) {
        this.monitor = monitor;
        this.reconciler = reconciler;
        this.operationRepository = operationRepository;
    }

    @GetMapping("/stuck")
    public List<OrderOperationReconciler.Assessment> stuckOperations() {
        return monitor.inspectNow();
    }

    @GetMapping("/orders/{orderId}")
    public List<OrderOperation> operationHistory(@PathVariable Long orderId) {
        return operationRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @PostMapping("/orders/{orderId}/actions")
    public ResponseEntity<OrderOperation> repair(
        @PathVariable Long orderId,
        @RequestBody RepairRequest request
    ) {
        OrderOperation operation = reconciler.repair(
            orderId,
            request.action(),
            request.reason(),
            request.idempotencyKey()
        );
        return ResponseEntity.ok(operation);
    }

    public record RepairRequest(
        OrderOperationReconciler.RepairAction action,
        String reason,
        String idempotencyKey
    ) {
    }
}
