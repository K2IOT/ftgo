package net.ftgo.consumer.service;

import net.ftgo.common.Money;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.domain.CreditReservation;
import net.ftgo.consumer.domain.CreditReservationStatus;
import net.ftgo.consumer.repository.ConsumerRepository;
import net.ftgo.consumer.repository.CreditReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditReservationServiceTest {

    @Mock
    private ConsumerRepository consumerRepository;

    @Mock
    private CreditReservationRepository reservationRepository;

    private CreditReservationService service;

    @BeforeEach
    void setUp() {
        service = new CreditReservationService(consumerRepository, reservationRepository);
    }

    @Test
    void reserveLocksConsumerAndReducesAvailableCreditOnce() {
        Consumer consumer = consumer(301L, "100.00");
        when(reservationRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(consumerRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(consumer));
        when(reservationRepository.save(any(CreditReservation.class)))
            .thenAnswer(invocation -> {
                CreditReservation reservation = invocation.getArgument(0);
                ReflectionTestUtils.setField(reservation, "id", 401L);
                return reservation;
            });

        CreditReservation reservation = service.reserve(301L, 101L, new Money("25.00"));

        assertThat(reservation.getId()).isEqualTo(401L);
        assertThat(reservation.getStatus()).isEqualTo(CreditReservationStatus.RESERVED);
        assertThat(consumer.getAvailableCredit()).isEqualTo(new Money("75.00"));
        verify(consumerRepository).save(consumer);
    }

    @Test
    void duplicateReserveReturnsEstablishedReservationWithoutSecondDebit() {
        CreditReservation existing = reservation(401L, 301L, 101L, "25.00");
        when(reservationRepository.findByOrderId(101L)).thenReturn(Optional.of(existing));

        CreditReservation result = service.reserve(301L, 101L, new Money("25.00"));

        assertThat(result).isSameAs(existing);
        verify(consumerRepository, never()).findByIdForUpdate(301L);
        verify(consumerRepository, never()).save(any());
    }

    @Test
    void duplicateReserveWithDifferentBusinessDataIsRejected() {
        CreditReservation existing = reservation(401L, 301L, 101L, "25.00");
        when(reservationRepository.findByOrderId(101L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.reserve(302L, 101L, new Money("30.00")))
            .isInstanceOf(CreditReservationException.class)
            .extracting("reasonCode")
            .isEqualTo(CreditReservationException.RESERVATION_CONFLICT);
    }

    @Test
    void commitAndReleaseAreIdempotent() {
        Consumer consumer = consumer(301L, "100.00");
        consumer.reserveCredit(new Money("25.00"));
        CreditReservation reservation = reservation(401L, 301L, 101L, "25.00");

        when(reservationRepository.findByOrderIdForUpdate(101L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(CreditReservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreditReservation committed = service.commit(301L, 101L);
        assertThat(committed.getStatus()).isEqualTo(CreditReservationStatus.COMMITTED);
        assertThat(service.commit(301L, 101L)).isSameAs(reservation);

        when(consumerRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(consumer));
        CreditReservation released = service.release(301L, 101L, "ORDER_REJECTED");
        assertThat(released.getStatus()).isEqualTo(CreditReservationStatus.RELEASED);
        assertThat(consumer.getAvailableCredit()).isEqualTo(new Money("100.00"));

        service.release(301L, 101L, "DUPLICATE");
        assertThat(consumer.getAvailableCredit()).isEqualTo(new Money("100.00"));
        verify(consumerRepository).save(consumer);
    }

    private Consumer consumer(Long id, String creditLimit) {
        Consumer consumer = new Consumer("Test", "test" + id + "@example.com", new Money(creditLimit));
        ReflectionTestUtils.setField(consumer, "id", id);
        return consumer;
    }

    private CreditReservation reservation(Long id, Long consumerId, Long orderId, String amount) {
        CreditReservation reservation = new CreditReservation(consumerId, orderId, new Money(amount));
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }
}
