package net.ftgo.orderhistory.api;

import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.common.security.PrincipalAccess;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.security.OrderHistoryAuthorizationService;
import net.ftgo.orderhistory.service.OrderHistoryQueryCriteria;
import net.ftgo.orderhistory.service.OrderHistoryQueryService;
import net.ftgo.orderhistory.service.UnsupportedOrderHistoryQueryException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class OrderHistoryController {

    private final OrderHistoryRepository orderHistoryRepository;
    private final OrderHistoryQueryService queryService;
    private final OrderHistoryAuthorizationService authorizationService;

    public OrderHistoryController(
        OrderHistoryRepository orderHistoryRepository,
        OrderHistoryQueryService queryService,
        OrderHistoryAuthorizationService authorizationService
    ) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.queryService = queryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderHistoryRecord> findOrder(
        @PathVariable String orderId,
        Authentication authentication
    ) {
        return orderHistoryRepository.findById(orderId)
            .map(record -> {
                FtgoPrincipal principal = PrincipalAccess.require(authentication);
                authorizationService.requireOrderAccess(record, principal);
                return ResponseEntity.ok(record);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/consumers/{consumerId}/orders")
    public ResponseEntity<OrderHistoryResponse> findOrderHistory(
        @PathVariable Long consumerId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDate since,
        @RequestParam(required = false) Long restaurantId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String pagingState,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        authorizationService.requireConsumerAccess(consumerId, principal);

        if (keyword != null && !keyword.isBlank()) {
            throw new UnsupportedOrderHistoryQueryException(
                "Keyword filtering requires an indexed search backend",
                "ORDER_HISTORY_KEYWORD_UNSUPPORTED"
            );
        }
        OrderHistoryQueryCriteria criteria = new OrderHistoryQueryCriteria(
            consumerId,
            status,
            restaurantId,
            since
        );
        return ResponseEntity.ok(queryService.query(criteria, pageSize, pagingState));
    }
}
