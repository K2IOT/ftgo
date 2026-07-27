package net.ftgo.accounting.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLedgerServiceTest {

    @Mock
    private PaymentLedgerEntryRepository repository;

    private PaymentLedgerService service;

    @BeforeEach
    void setUp() {
        service = new PaymentLedgerService(repository);
    }

    @Test
    void appendsAnImmutableLedgerEntryOnce() {
        when(repository.findByRequestId("capture-101")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(PaymentLedgerEntry.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentLedgerEntry entry = service.append(
            1L,
            101L,
            201L,
            PaymentLedgerEntry.OperationType.CAPTURE,
            "capture-101",
            new BigDecimal("42.50"),
            "provider-capture-101"
        );

        assertThat(entry.getOperationType()).isEqualTo(PaymentLedgerEntry.OperationType.CAPTURE);
        assertThat(entry.getAmount()).isEqualByComparingTo("42.50");
        assertThat(entry.getCurrency()).isEqualTo("VND");
        assertThat(entry.getProviderReference()).isEqualTo("provider-capture-101");
        verify(repository).saveAndFlush(entry);
    }

    @Test
    void sameRequestAndMetadataReplaysExistingEntryWithoutWriting() {
        PaymentLedgerEntry existing = new PaymentLedgerEntry(
            1L,
            101L,
            201L,
            PaymentLedgerEntry.OperationType.REFUND,
            "refund-101-1",
            new BigDecimal("10.00"),
            "provider-refund-101-1"
        );
        when(repository.findByRequestId("refund-101-1")).thenReturn(Optional.of(existing));

        PaymentLedgerEntry replayed = service.append(
            1L,
            101L,
            201L,
            PaymentLedgerEntry.OperationType.REFUND,
            "refund-101-1",
            new BigDecimal("10.00"),
            "provider-refund-101-1"
        );

        assertThat(replayed).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reusedRequestWithDifferentMetadataIsRejected() {
        PaymentLedgerEntry existing = new PaymentLedgerEntry(
            1L,
            101L,
            201L,
            PaymentLedgerEntry.OperationType.REFUND,
            "refund-101-1",
            new BigDecimal("10.00"),
            "provider-refund-101-1"
        );
        when(repository.findByRequestId("refund-101-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.append(
            1L,
            101L,
            201L,
            PaymentLedgerEntry.OperationType.REFUND,
            "refund-101-1",
            new BigDecimal("11.00"),
            "provider-refund-101-1"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("request ID conflict");

        assertThatThrownBy(() -> service.append(
            1L,
            101L,
            201L,
            PaymentLedgerEntry.OperationType.REFUND,
            "refund-101-1",
            new BigDecimal("10.00"),
            "another-provider-reference"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("request ID conflict");
    }
}