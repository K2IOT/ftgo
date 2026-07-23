package net.ftgo.consumer.service;

import net.ftgo.common.Money;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.domain.CreditReservation;
import net.ftgo.consumer.domain.CreditReservationStatus;
import net.ftgo.consumer.repository.ConsumerRepository;
import net.ftgo.consumer.repository.CreditReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditReservationService {

    private final ConsumerRepository consumerRepository;
    private final CreditReservationRepository reservationRepository;

    public CreditReservationService(ConsumerRepository consumerRepository,
                                    CreditReservationRepository reservationRepository) {
        this.consumerRepository = consumerRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public CreditReservation reserve(Long consumerId, Long orderId, Money amount) {
        CreditReservation existing = reservationRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return verifyDuplicate(existing, consumerId, amount);
        }

        Consumer consumer = consumerRepository.findByIdForUpdate(consumerId)
            .orElseThrow(() -> failure(CreditReservationException.CONSUMER_NOT_FOUND,
                "Consumer not found: " + consumerId));

        existing = reservationRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return verifyDuplicate(existing, consumerId, amount);
        }

        try {
            consumer.reserveCredit(amount);
        } catch (IllegalArgumentException e) {
            throw failure(CreditReservationException.INSUFFICIENT_CREDIT, e.getMessage());
        }

        consumerRepository.save(consumer);
        return reservationRepository.save(new CreditReservation(consumerId, orderId, amount));
    }

    @Transactional
    public CreditReservation commit(Long consumerId, Long orderId) {
        CreditReservation reservation = findForUpdate(consumerId, orderId);
        try {
            reservation.commit();
        } catch (IllegalStateException e) {
            throw failure(CreditReservationException.INVALID_RESERVATION_STATE, e.getMessage());
        }
        return reservationRepository.save(reservation);
    }

    @Transactional
    public CreditReservation release(Long consumerId, Long orderId, String reason) {
        CreditReservation reservation = findForUpdate(consumerId, orderId);
        if (reservation.getStatus() == CreditReservationStatus.RELEASED) {
            return reservation;
        }

        Consumer consumer = consumerRepository.findByIdForUpdate(consumerId)
            .orElseThrow(() -> failure(CreditReservationException.CONSUMER_NOT_FOUND,
                "Consumer not found: " + consumerId));
        consumer.releaseCredit(reservation.getAmount());
        reservation.release();
        consumerRepository.save(consumer);
        return reservationRepository.save(reservation);
    }

    private CreditReservation findForUpdate(Long consumerId, Long orderId) {
        CreditReservation reservation = reservationRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> failure(CreditReservationException.RESERVATION_NOT_FOUND,
                "Credit reservation not found for order: " + orderId));
        if (!reservation.getConsumerId().equals(consumerId)) {
            throw failure(CreditReservationException.RESERVATION_CONFLICT,
                "Reservation belongs to another consumer");
        }
        return reservation;
    }

    private CreditReservation verifyDuplicate(CreditReservation existing,
                                              Long consumerId,
                                              Money amount) {
        if (!existing.matches(consumerId, amount)) {
            throw failure(CreditReservationException.RESERVATION_CONFLICT,
                "Order already has a different credit reservation");
        }
        return existing;
    }

    private CreditReservationException failure(String reasonCode, String message) {
        return new CreditReservationException(reasonCode, message);
    }
}
