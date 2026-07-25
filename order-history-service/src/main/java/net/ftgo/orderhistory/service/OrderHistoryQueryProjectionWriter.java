package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.OrderHistoryByConsumer;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerKey;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerRestaurant;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerRestaurantKey;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerStatus;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerStatusKey;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerRepository;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerRestaurantRepository;
import net.ftgo.orderhistory.repository.OrderHistoryByConsumerStatusRepository;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Component
public class OrderHistoryQueryProjectionWriter {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final OrderHistoryByConsumerRepository consumerRepository;
    private final OrderHistoryByConsumerStatusRepository statusRepository;
    private final OrderHistoryByConsumerRestaurantRepository restaurantRepository;

    public OrderHistoryQueryProjectionWriter(
        OrderHistoryByConsumerRepository consumerRepository,
        OrderHistoryByConsumerStatusRepository statusRepository,
        OrderHistoryByConsumerRestaurantRepository restaurantRepository
    ) {
        this.consumerRepository = consumerRepository;
        this.statusRepository = statusRepository;
        this.restaurantRepository = restaurantRepository;
    }

    public void upsert(OrderHistoryRecord record, String previousStatus) {
        if (record.getConsumerId() == null || record.getCreationDate() == null) {
            return;
        }
        String month = YearMonth.from(record.getCreationDate()).format(MONTH_FORMAT);
        OrderHistoryByConsumerKey consumerKey = new OrderHistoryByConsumerKey(
            record.getConsumerId(),
            month,
            record.getCreationDate(),
            record.getOrderId()
        );
        consumerRepository.save(new OrderHistoryByConsumer(consumerKey, record));

        if (previousStatus != null && !previousStatus.equals(record.getStatus())) {
            statusRepository.deleteById(new OrderHistoryByConsumerStatusKey(
                record.getConsumerId(),
                previousStatus,
                month,
                record.getCreationDate(),
                record.getOrderId()
            ));
        }
        if (record.getStatus() != null) {
            statusRepository.save(new OrderHistoryByConsumerStatus(
                new OrderHistoryByConsumerStatusKey(
                    record.getConsumerId(),
                    record.getStatus(),
                    month,
                    record.getCreationDate(),
                    record.getOrderId()
                ),
                record
            ));
        }

        if (record.getRestaurantId() != null) {
            restaurantRepository.save(new OrderHistoryByConsumerRestaurant(
                new OrderHistoryByConsumerRestaurantKey(
                    record.getConsumerId(),
                    record.getRestaurantId(),
                    month,
                    record.getCreationDate(),
                    record.getOrderId()
                ),
                record
            ));
        }
    }
}
