package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("order_history_by_consumer_status_v2")
public class OrderHistoryByConsumerStatus extends OrderHistoryQueryRow {

    @PrimaryKey
    private OrderHistoryByConsumerStatusKey key;

    public OrderHistoryByConsumerStatus() {
    }

    public OrderHistoryByConsumerStatus(OrderHistoryByConsumerStatusKey key, OrderHistoryRecord record) {
        this.key = key;
        copyFrom(record);
    }

    public OrderHistoryByConsumerStatusKey getKey() { return key; }

    @Override
    public String orderId() { return key.getOrderId(); }
}
