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
import java.nio.charset.StandardCharsets;
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
        String pagingState
    ) {
        Cursor cursor = decodeCursor(pagingState);
        YearMonth currentMonth = cursor == null
            ? YearMonth.now(ZoneOffset.UTC)
            : cursor.month();
        String driverState = cursor == null ? null : cursor.driverState();
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
                String nextState = nextDriverState(slice);
                return new QueryPage(
                    records,
                    encodeCursor(new Cursor(currentMonth, nextState))
                );
            }

            currentMonth = currentMonth.minusMonths(1);
            driverState = null;
            if (records.size() == pageSize) {
                String next = currentMonth.isBefore(lowerBound)
                    ? null
                    : encodeCursor(new Cursor(currentMonth, null));
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

    private String encodeCursor(Cursor cursor) {
        String raw = cursor.month().format(MONTH_FORMAT)
            + "\n" + (cursor.driverState() == null ? "" : cursor.driverState());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            raw.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Cursor decodeCursor(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(
                Base64.getUrlDecoder().decode(token),
                StandardCharsets.UTF_8
            );
            String[] parts = raw.split("\\n", 2);
            YearMonth month = YearMonth.parse(parts[0], MONTH_FORMAT);
            String state = parts.length == 2 && !parts[1].isBlank() ? parts[1] : null;
            return new Cursor(month, state);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid paging token", e);
        }
    }

    private record Cursor(YearMonth month, String driverState) {
    }
}
