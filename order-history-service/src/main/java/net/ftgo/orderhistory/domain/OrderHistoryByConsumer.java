package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;

@Table("order_history_by_consumer_v2")
public class OrderHistoryByConsumer extends OrderHistoryQueryRow {

    @PrimaryKey
    private OrderHistoryByConsumerKey key;

    @Column("restaurant_id")
    private Long restaurantId;

    @Column("status")
    private String status;

    public OrderHistoryByConsumer() {
    }

    public OrderHistoryByConsumer(OrderHistoryByConsumerKey key, OrderHistoryRecord record) {
        this.key = key;
        this.restaurantId = record.getRestaurantId();
        this.status = record.getStatus();
        copyFrom(record);
    }

    public OrderHistoryByConsumerKey getKey() { return key; }

    @Override
    protected Long consumerId() { return key.getConsumerId(); }

    @Override
    protected Long restaurantId() { return restaurantId; }

    @Override
    protected String status() { return status; }

    @Override
    protected LocalDateTime creationDate() { return key.getCreationDate(); }

    @Override
    public String orderId() { return key.getOrderId(); }
}
