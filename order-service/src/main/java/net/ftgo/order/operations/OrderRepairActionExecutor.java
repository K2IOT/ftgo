package net.ftgo.order.operations;

import net.ftgo.order.domain.Order;

public interface OrderRepairActionExecutor {

    void restart(Order order, OrderOperationType operationType);

    void compensate(Order order, OrderOperationType operationType);
}
