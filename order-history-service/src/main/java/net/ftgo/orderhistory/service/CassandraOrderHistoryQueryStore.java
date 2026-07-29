package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.OrderHistoryByConsumer;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerRestaurant;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerStatus;
import net.ftgo.orderhistory.domain.OrderHistoryQueryRow;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerRepository;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerRestaurantRepository;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerStatusRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class CassandraOrderHistoryQueryStore implements OrderHistoryQueryStore {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final OrderHistoryByConsumerRepository consumerRepository;
    private final OrderHistoryByConsumerStatusRepository statusRepository;
    private final OrderHistoryByConsumerRestaurantRepository restaurantRepository;
    private final int historyMonths;

    public CassandraOrderHistoryQueryStore(
        OrderHistoryByConsumerRepository consumerRepository,
        OrderHistoryByConsumerStatusRepository statusRepository,
        OrderHistoryByConsumerRestaurantRepository restaurantRepository,
        @Value("${ftgo.order-history.query-history-months:120}") int historyMonths
    ) {
        this.consumerRepository = consumerRepository;
        this.statusRepository = statusRepository;
        this.restaurantRepository = restaurantRepository;
        this.historyMonths = Math.max(1, historyMonths);
    }

    @Override
    public QueryPage fetch(
        OrderHistoryQueryCriteria criteria,
        int pageSize,
        OrderHistoryPageCursor cursor
    ) {
        YearMonth currentMonth = cursor == null
            ? YearMonth.now(ZoneOffset.UTC)
            : cursor.bucketMonth();
        String driverState = cursor == null ? null : cursor.driverPagingState();
        YearMonth lowerBound = criteria.since() == null
            ? YearMonth.now(ZoneOffset.UTC).minusMonths(historyMonths - 1L)
            : YearMonth.from(criteria.since());

        List<OrderHistoryRecord> records = new ArrayList<>(pageSize);
        while (records.size() < pageSize && !currentMonth.isBefore(lowerBound)) {
            int remaining = pageSize - records.size();
            CassandraPageRequest request = pageRequest(remaining, driverState);
            Slice<? extends OrderHistoryQueryRow> slice = queryBucket(
                criteria,
                currentMonth,
                request,
                lowerBound.equals(currentMonth)
                    && criteria.since() != null
                    ? criteria.since().atStartOfDay()
                    : null
            );
            slice.getContent().stream()
                .map(OrderHistoryQueryRow::toRecord)
                .forEach(records::add);

            if (slice.hasNext()) {
                return new QueryPage(
                    records,
                    new OrderHistoryPageCursor(currentMonth, nextDriverState(slice))
                );
            }

            currentMonth = currentMonth.minusMonths(1);
            driverState = null;
            if (records.size() == pageSize) {
                OrderHistoryPageCursor next = currentMonth.isBefore(lowerBound)
                    ? null
                    : new OrderHistoryPageCursor(currentMonth, null);
                return new QueryPage(records, next);
            }
        }
        return new QueryPage(records, null);
    }

    private Slice<? extends OrderHistoryQueryRow> queryBucket(
        OrderHistoryQueryCriteria criteria,
        YearMonth month,
        Pageable pageable,
        LocalDateTime since
    ) {
        String bucket = month.format(MONTH_FORMAT);
        return switch (criteria.kind()) {
            case CONSUMER -> since == null
                ? consumerRepository.findPage(criteria.consumerId(), bucket, pageable)
                : consumerRepository.findPageSince(criteria.consumerId(), bucket, since, pageable);
            case CONSUMER_STATUS -> since == null
                ? statusRepository.findPage(criteria.consumerId(), criteria.status(), bucket, pageable)
                : statusRepository.findPageSince(
                    criteria.consumerId(),
                    criteria.status(),
                    bucket,
                    since,
                    pageable
                );
            case CONSUMER_RESTAURANT -> since == null
                ? restaurantRepository.findPage(
                    criteria.consumerId(),
                    criteria.restaurantId(),
                    bucket,
                    pageable
                )
                : restaurantRepository.findPageSince(
                    criteria.consumerId(),
                    criteria.restaurantId(),
                    bucket,
                    since,
                    pageable
                );
        };
    }

    private CassandraPageRequest pageRequest(int pageSize, String driverState) {
        CassandraPageRequest first = CassandraPageRequest.first(pageSize);
        if (driverState == null || driverState.isBlank()) {
            return first;
        }
        try {
            ByteBuffer state = ByteBuffer.wrap(Base64.getUrlDecoder().decode(driverState));
            return CassandraPageRequest.of(first, state);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Cassandra paging state", e);
        }
    }

    private String nextDriverState(Slice<?> slice) {
        Pageable next = slice.nextPageable();
        if (!(next instanceof CassandraPageRequest request)) {
            throw new IllegalStateException("Cassandra slice did not expose a CassandraPageRequest");
        }
        ByteBuffer buffer = request.getPagingState();
        if (buffer == null) {
            throw new IllegalStateException("Cassandra slice hasNext but no paging state");
        }
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
