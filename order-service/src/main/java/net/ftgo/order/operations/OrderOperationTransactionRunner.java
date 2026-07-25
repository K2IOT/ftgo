package net.ftgo.order.operations;

import java.util.function.Supplier;

/** Executes repair workflow stages with explicit transaction propagation. */
public interface OrderOperationTransactionRunner {

    <T> T required(Supplier<T> callback);

    <T> T requiresNew(Supplier<T> callback);

    static OrderOperationTransactionRunner direct() {
        return new OrderOperationTransactionRunner() {
            @Override
            public <T> T required(Supplier<T> callback) {
                return callback.get();
            }

            @Override
            public <T> T requiresNew(Supplier<T> callback) {
                return callback.get();
            }
        };
    }
}
