package net.ftgo.order.operations;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Component
public class SpringOrderOperationTransactionRunner implements OrderOperationTransactionRunner {

    @Override
    @Transactional
    public <T> T required(Supplier<T> callback) {
        return callback.get();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T requiresNew(Supplier<T> callback) {
        return callback.get();
    }
}
