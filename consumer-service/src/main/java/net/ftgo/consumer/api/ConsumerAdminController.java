package net.ftgo.consumer.api;

import jakarta.validation.Valid;
import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.common.security.PrincipalAccess;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.security.ConsumerAuthorizationService;
import net.ftgo.consumer.service.ConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/consumers")
public class ConsumerAdminController {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerAdminController.class);

    private final ConsumerService consumerService;
    private final ConsumerAuthorizationService authorizationService;

    public ConsumerAdminController(
        ConsumerService consumerService,
        ConsumerAuthorizationService authorizationService
    ) {
        this.consumerService = consumerService;
        this.authorizationService = authorizationService;
    }

    @PutMapping("/{id}/credit-limit")
    public ResponseEntity<ConsumerResponse> updateCreditLimit(
        @PathVariable Long id,
        @Valid @RequestBody UpdateConsumerCreditLimitRequest request,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        authorizationService.requireAdmin(principal);
        logger.info(
            "Admin credit limit update actorSubject={} consumerId={}",
            principal.subject(),
            id
        );

        Consumer consumer = consumerService.updateCreditLimit(
            id,
            new Money(request.getCreditLimit())
        );
        return ResponseEntity.ok(new ConsumerResponse(consumer));
    }
}
