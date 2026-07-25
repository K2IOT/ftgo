package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;

@Table("order_history_by_consumer_status_v2")
public class OrderHistoryByConsumerStatus extends OrderHistoryQueryRow {

    @PrimaryKey
    private OrderHistoryByConsumerStatusKey key;

    @Column("restaurant_id")
    private Long restaurantId;

    public OrderHistoryByConsumerStatus() {
    }

    public OrderHistoryByConsumerStatus(OrderHistoryByConsumerStatusKey key, OrderHistoryRecord record) {
        this.key = key;
        this.restaurantId = record.getRestaurantId();
        copyFrom(record);
    }

    public OrderHistoryByConsumerStatusKey getKey() { return key; }

    @Override
    protected Long consumerId() { return key.getConsumerId(); }

    @Override
    protected Long restaurantId() { return restaurantId; }

    @Override
    protected String status() { return key.getStatus(); }

    @Override
    protected LocalDateTime creationDate() { return key.getCreationDate(); }

    @Override
    public String orderId() { return key.getOrderId(); }
}
