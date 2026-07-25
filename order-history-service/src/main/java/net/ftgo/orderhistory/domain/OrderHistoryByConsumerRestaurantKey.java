package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@PrimaryKeyClass
public class OrderHistoryByConsumerRestaurantKey implements Serializable {

    @PrimaryKeyColumn(name = "consumer_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private Long consumerId;

    @PrimaryKeyColumn(name = "restaurant_id", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private Long restaurantId;

    @PrimaryKeyColumn(name = "creation_month", ordinal = 2, type = PrimaryKeyType.PARTITIONED)
    private String creationMonth;

    @PrimaryKeyColumn(name = "creation_date", ordinal = 3, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private LocalDateTime creationDate;

    @PrimaryKeyColumn(name = "order_id", ordinal = 4, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private String orderId;

    public OrderHistoryByConsumerRestaurantKey() {
    }

    public OrderHistoryByConsumerRestaurantKey(Long consumerId, Long restaurantId, String creationMonth, LocalDateTime creationDate, String orderId) {
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.creationMonth = creationMonth;
        this.creationDate = creationDate;
        this.orderId = orderId;
    }

    public Long getConsumerId() { return consumerId; }
    public Long getRestaurantId() { return restaurantId; }
    public String getCreationMonth() { return creationMonth; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public String getOrderId() { return orderId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrderHistoryByConsumerRestaurantKey that)) return false;
        return Objects.equals(consumerId, that.consumerId)
            && Objects.equals(restaurantId, that.restaurantId)
            && Objects.equals(creationMonth, that.creationMonth)
            && Objects.equals(creationDate, that.creationDate)
            && Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerId, restaurantId, creationMonth, creationDate, orderId);
    }
}
