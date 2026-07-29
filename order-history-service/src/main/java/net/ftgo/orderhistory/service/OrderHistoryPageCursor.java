package net.ftgo.orderhistory.service;

import java.time.YearMonth;
import java.util.Objects;

public record OrderHistoryPageCursor(
    YearMonth bucketMonth,
    String driverPagingState
) {

    public OrderHistoryPageCursor {
        Objects.requireNonNull(bucketMonth, "bucketMonth is required");
        if (driverPagingState != null && driverPagingState.isBlank()) {
            driverPagingState = null;
        }
    }
}
