package net.ftgo.orderhistory.service;

import java.time.LocalDate;

public record OrderHistoryQueryCriteria(
    Long consumerId,
    String status,
    Long restaurantId,
    LocalDate since
) {

    public OrderHistoryQueryCriteria {
        if (consumerId == null) {
            throw new IllegalArgumentException("consumerId is required");
        }
        if (status != null && status.isBlank()) {
            status = null;
        }
    }

    public QueryKind kind() {
        if (status != null && restaurantId != null) {
            throw new UnsupportedOrderHistoryQueryException(
                "Combining status and restaurantId is not supported",
                "ORDER_HISTORY_FILTER_COMBINATION_UNSUPPORTED"
            );
        }
        if (status != null) return QueryKind.CONSUMER_STATUS;
        if (restaurantId != null) return QueryKind.CONSUMER_RESTAURANT;
        return QueryKind.CONSUMER;
    }

    public enum QueryKind {
        CONSUMER,
        CONSUMER_STATUS,
        CONSUMER_RESTAURANT
    }
}
