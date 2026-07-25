package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("order_history_by_consumer_restaurant_v2")
public class OrderHistoryByConsumerRestaurant extends OrderHistoryQueryRow {

    @PrimaryKey
    private OrderHistoryByConsumerRestaurantKey key;

    public OrderHistoryByConsumerRestaurant() {
    }

    public OrderHistoryByConsumerRestaurant(OrderHistoryByConsumerRestaurantKey key, OrderHistoryRecord record) {
        this.key = key;
        copyFrom(record);
    }

    public OrderHistoryByConsumerRestaurantKey getKey() { return key; }

    @Override
    public String orderId() { return key.getOrderId(); }
}
