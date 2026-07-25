package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;

@Table("order_history_by_consumer_restaurant_v2")
public class OrderHistoryByConsumerRestaurant extends OrderHistoryQueryRow {

    @PrimaryKey
    private OrderHistoryByConsumerRestaurantKey key;

    @Column("status")
    private String status;

    public OrderHistoryByConsumerRestaurant() {
    }

    public OrderHistoryByConsumerRestaurant(OrderHistoryByConsumerRestaurantKey key, OrderHistoryRecord record) {
        this.key = key;
        this.status = record.getStatus();
        copyFrom(record);
    }

    public OrderHistoryByConsumerRestaurantKey getKey() { return key; }

    @Override
    protected Long consumerId() { return key.getConsumerId(); }

    @Override
    protected Long restaurantId() { return key.getRestaurantId(); }

    @Override
    protected String status() { return status; }

    @Override
    protected LocalDateTime creationDate() { return key.getCreationDate(); }

    @Override
    public String orderId() { return key.getOrderId(); }
}
