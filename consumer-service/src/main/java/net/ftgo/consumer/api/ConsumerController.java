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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** REST API controller for consumer registration and profile operations. */
@RestController
@RequestMapping("/consumers")
public class ConsumerController {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerController.class);

    private final ConsumerService consumerService;
    private final ConsumerAuthorizationService authorizationService;
    private final Money registrationCreditLimit;

    public ConsumerController(
        ConsumerService consumerService,
        ConsumerAuthorizationService authorizationService,
        @Value("${ftgo.consumer.registration-credit-limit:100.00}") BigDecimal registrationCreditLimit
    ) {
        this.consumerService = consumerService;
        this.authorizationService = authorizationService;
        this.registrationCreditLimit = new Money(registrationCreditLimit);
    }

    @PostMapping
    public ResponseEntity<ConsumerResponse> createConsumer(
        @Valid @RequestBody CreateConsumerRequest request,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        logger.info(
            "Creating consumer actorSubject={} email={} with server registration policy",
            principal.subject(),
            request.getEmail()
        );

        Consumer consumer = consumerService.createConsumer(
            request.getName(),
            request.getEmail(),
            registrationCreditLimit
        );

        logger.info(
            "Consumer created actorSubject={} consumerId={}",
            principal.subject(),
            consumer.getId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new ConsumerResponse(consumer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConsumerResponse> getConsumer(
        @PathVariable Long id,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        authorizationService.requireConsumerAccess(id, principal);
        logger.debug(
            "Fetching consumer actorSubject={} consumerId={}",
            principal.subject(),
            id
        );

        Consumer consumer = consumerService.findConsumer(id);
        return ResponseEntity.ok(new ConsumerResponse(consumer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConsumerResponse> updateConsumer(
        @PathVariable Long id,
        @Valid @RequestBody UpdateConsumerRequest request,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        authorizationService.requireConsumerAccess(id, principal);
        logger.info(
            "Updating consumer actorSubject={} consumerId={}",
            principal.subject(),
            id
        );

        Consumer consumer = consumerService.updateConsumer(
            id,
            request.getName(),
            request.getEmail()
        );

        logger.info("Consumer updated consumerId={}", consumer.getId());
        return ResponseEntity.ok(new ConsumerResponse(consumer));
    }
}
