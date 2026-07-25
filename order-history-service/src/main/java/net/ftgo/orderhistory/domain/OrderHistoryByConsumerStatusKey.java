package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@PrimaryKeyClass
public class OrderHistoryByConsumerStatusKey implements Serializable {

    @PrimaryKeyColumn(name = "consumer_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private Long consumerId;

    @PrimaryKeyColumn(name = "status", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private String status;

    @PrimaryKeyColumn(name = "creation_month", ordinal = 2, type = PrimaryKeyType.PARTITIONED)
    private String creationMonth;

    @PrimaryKeyColumn(name = "creation_date", ordinal = 3, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private LocalDateTime creationDate;

    @PrimaryKeyColumn(name = "order_id", ordinal = 4, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private String orderId;

    public OrderHistoryByConsumerStatusKey() {
    }

    public OrderHistoryByConsumerStatusKey(Long consumerId, String status, String creationMonth, LocalDateTime creationDate, String orderId) {
        this.consumerId = consumerId;
        this.status = status;
        this.creationMonth = creationMonth;
        this.creationDate = creationDate;
        this.orderId = orderId;
    }

    public Long getConsumerId() { return consumerId; }
    public String getStatus() { return status; }
    public String getCreationMonth() { return creationMonth; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public String getOrderId() { return orderId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrderHistoryByConsumerStatusKey that)) return false;
        return Objects.equals(consumerId, that.consumerId)
            && Objects.equals(status, that.status)
            && Objects.equals(creationMonth, that.creationMonth)
            && Objects.equals(creationDate, that.creationDate)
            && Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerId, status, creationMonth, creationDate, orderId);
    }
}
