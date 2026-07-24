package net.ftgo.kitchen.service;

import net.ftgo.common.orderflow.events.TicketAcceptanceTimedOutEvent;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/** Claims expired acceptance decisions one ticket per transaction. */
@Service
public class TicketAcceptanceTimeoutService {

    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public TicketAcceptanceTimeoutService(
        TicketRepository ticketRepository,
        DomainEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate,
        @Value("${ftgo.kitchen.acceptance-timeout-batch-size:100}") int batchSize
    ) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ftgo.kitchen.acceptance-timeout-scan-ms:5000}")
    public void expireDueTickets() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> candidateIds = ticketRepository.findDueAcceptanceTimeoutIds(
            now,
            PageRequest.of(0, batchSize)
        );
        candidateIds.forEach(ticketId -> expireOne(ticketId, now));
    }

    public boolean expireOne(Long ticketId, LocalDateTime now) {
        Boolean changed = transactionTemplate.execute(status -> {
            Ticket ticket = ticketRepository.findByIdForUpdate(ticketId).orElse(null);
            if (ticket == null || !ticket.timeout(now)) {
                return false;
            }

            ticketRepository.saveAndFlush(ticket);
            eventPublisher.publishTicketEvent(
                ticket.getId(),
                ticket.getVersion(),
                new TicketAcceptanceTimedOutEvent(
                    ticket.getDecisionEventId(),
                    ticket.getId(),
                    ticket.getOrderId(),
                    ticket.getAcceptanceDeadline(),
                    ticket.getDecisionAt()
                )
            );
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }
}
