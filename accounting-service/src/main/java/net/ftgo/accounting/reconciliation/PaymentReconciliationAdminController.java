package net.ftgo.accounting.reconciliation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/admin/payments/reconciliation-cases")
public class PaymentReconciliationAdminController {

    private final PaymentReconciliationCaseRepository caseRepository;

    public PaymentReconciliationAdminController(
        PaymentReconciliationCaseRepository caseRepository
    ) {
        this.caseRepository = caseRepository;
    }

    @GetMapping
    public List<CaseView> list(
        @RequestParam(defaultValue = "OPEN") PaymentReconciliationCaseStatus status
    ) {
        return caseRepository.findByStatusOrderByFirstDetectedAtAsc(status).stream()
            .map(CaseView::from)
            .toList();
    }

    public record CaseView(
        Long id,
        String caseKey,
        Long authorizationId,
        String providerAuthorizationId,
        String providerReference,
        PaymentReconciliationCaseType caseType,
        PaymentReconciliationSeverity severity,
        PaymentReconciliationCaseStatus status,
        BigDecimal expectedAmount,
        BigDecimal providerAmount,
        String summary,
        Integer occurrences,
        Instant firstDetectedAt,
        Instant lastDetectedAt
    ) {
        static CaseView from(PaymentReconciliationCase value) {
            return new CaseView(
                value.getId(),
                value.getCaseKey(),
                value.getAuthorizationId(),
                value.getProviderAuthorizationId(),
                value.getProviderReference(),
                value.getCaseType(),
                value.getSeverity(),
                value.getStatus(),
                value.getExpectedAmount(),
                value.getProviderAmount(),
                value.getSummary(),
                value.getOccurrences(),
                value.getFirstDetectedAt(),
                value.getLastDetectedAt()
            );
        }
    }
}
