package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("order_history_by_consumer_v2")
public class OrderHistoryByConsumer extends OrderHistoryQueryRow {

    @PrimaryKey
    private OrderHistoryByConsumerKey key;

    public OrderHistoryByConsumer() {
    }

    public OrderHistoryByConsumer(OrderHistoryByConsumerKey key, OrderHistoryRecord record) {
        this.key = key;
        copyFrom(record);
    }

    public OrderHistoryByConsumerKey getKey() { return key; }

    @Override
    public String orderId() { return key.getOrderId(); }
}
