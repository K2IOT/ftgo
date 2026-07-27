package net.ftgo.accounting.api.admin;

import net.ftgo.accounting.settlement.ManualPaymentSettlementService;
import net.ftgo.accounting.settlement.SettlementDiscrepancy;
import net.ftgo.accounting.settlement.SettlementReconciler;
import net.ftgo.accounting.settlement.SettlementReconciliationReport;
import net.ftgo.accounting.settlement.SettlementRepairAction;
import net.ftgo.common.Money;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payment-settlement")
public class PaymentSettlementOperationsController {

    private final SettlementReconciler reconciler;
    private final ManualPaymentSettlementService manualSettlementService;

    public PaymentSettlementOperationsController(
        SettlementReconciler reconciler,
        ManualPaymentSettlementService manualSettlementService
    ) {
        this.reconciler = reconciler;
        this.manualSettlementService = manualSettlementService;
    }

    @GetMapping("/discrepancies")
    public List<SettlementDiscrepancy> discrepancies() {
        return reconciler.findAll();
    }

    @GetMapping("/authorizations/{authorizationId}/discrepancies")
    public List<SettlementDiscrepancy> discrepanciesByAuthorization(
        @PathVariable Long authorizationId
    ) {
        return reconciler.findByAuthorization(authorizationId);
    }

    @PostMapping("/authorizations/{authorizationId}/refunds")
    public ResponseEntity<ManualPaymentSettlementService.RefundResult> refund(
        @PathVariable Long authorizationId,
        @RequestBody RefundRequest request
    ) {
        return ResponseEntity.ok(manualSettlementService.refund(
            authorizationId,
            request.amount(),
            request.reason(),
            request.idempotencyKey()
        ));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<SettlementReconciliationReport> reconcile() {
        return ResponseEntity.ok(reconciler.scan());
    }

    @PostMapping("/discrepancies/{discrepancyId}/actions")
    public ResponseEntity<SettlementDiscrepancy> repair(
        @PathVariable Long discrepancyId,
        @RequestBody RepairRequest request
    ) {
        return ResponseEntity.ok(reconciler.repair(
            discrepancyId,
            request.action(),
            request.idempotencyKey(),
            request.reason()
        ));
    }

    public record RefundRequest(
        Money amount,
        String reason,
        String idempotencyKey
    ) {
    }

    public record RepairRequest(
        SettlementRepairAction action,
        String idempotencyKey,
        String reason
    ) {
    }
}