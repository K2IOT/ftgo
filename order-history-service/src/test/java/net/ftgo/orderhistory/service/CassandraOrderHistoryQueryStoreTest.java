package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.OrderHistoryByConsumer;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerRepository;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerRestaurantRepository;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CassandraOrderHistoryQueryStoreTest {

    private OrderHistoryByConsumerRepository consumerRepository;
    private OrderHistoryByConsumerStatusRepository statusRepository;
    private OrderHistoryByConsumerRestaurantRepository restaurantRepository;

    @BeforeEach
    void setUp() {
        consumerRepository = mock(OrderHistoryByConsumerRepository.class);
        statusRepository = mock(OrderHistoryByConsumerStatusRepository.class);
        restaurantRepository = mock(OrderHistoryByConsumerRestaurantRepository.class);
        when(consumerRepository.findPage(anyLong(), anyString(), any(Pageable.class)))
            .thenReturn(new SliceImpl<OrderHistoryByConsumer>(List.of()));
    }

    @Test
    void capsEmptyHistoryScanAtTwentyFourBucketsAndReturnsFirstUnreadBucket() {
        CassandraOrderHistoryQueryStore store = new CassandraOrderHistoryQueryStore(
            consumerRepository,
            statusRepository,
            restaurantRepository,
            120,
            24
        );

        OrderHistoryQueryStore.QueryPage page = store.fetch(
            new OrderHistoryQueryCriteria(42L, null, null, null),
            20,
            null
        );

        verify(consumerRepository, times(24))
            .findPage(anyLong(), anyString(), any(Pageable.class));
        verify(statusRepository, never())
            .findPage(anyLong(), anyString(), anyString(), any(Pageable.class));
        verify(restaurantRepository, never())
            .findPage(anyLong(), anyLong(), anyString(), any(Pageable.class));
        assertNotNull(page.nextCursor());
        assertEquals(
            YearMonth.now(ZoneOffset.UTC).minusMonths(24),
            page.nextCursor().bucketMonth()
        );
    }
}
