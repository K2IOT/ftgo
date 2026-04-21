package net.ftgo.common.channels;

/**
 * Centralized Kafka channel names for command channels and saga reply channels.
 * Domain event topics follow the pattern: net.ftgo.{service}.domain.{Aggregate}
 */
public class ChannelNames {
    
    // Command Channels (for saga participants)
    public static final String ORDER_SERVICE_COMMAND_CHANNEL = "orderService";
    public static final String CONSUMER_SERVICE_COMMAND_CHANNEL = "consumerService";
    public static final String KITCHEN_SERVICE_COMMAND_CHANNEL = "kitchenService";
    public static final String ACCOUNTING_SERVICE_COMMAND_CHANNEL = "accountingService";
    public static final String DELIVERY_SERVICE_COMMAND_CHANNEL = "deliveryService";
    
    // Saga Reply Channels
    public static final String CREATE_ORDER_SAGA_REPLY_CHANNEL = "createOrderSagaReply";
    public static final String CANCEL_ORDER_SAGA_REPLY_CHANNEL = "cancelOrderSagaReply";
    public static final String REVISE_ORDER_SAGA_REPLY_CHANNEL = "reviseOrderSagaReply";
    
    // Domain Event Topics
    public static final String ORDER_EVENT_TOPIC = "net.ftgo.orderservice.domain.Order";
    public static final String CONSUMER_EVENT_TOPIC = "net.ftgo.consumerservice.domain.Consumer";
    public static final String RESTAURANT_EVENT_TOPIC = "net.ftgo.restaurantservice.domain.Restaurant";
    public static final String TICKET_EVENT_TOPIC = "net.ftgo.kitchenservice.domain.Ticket";
    public static final String ACCOUNT_EVENT_TOPIC = "net.ftgo.accountingservice.domain.Account";
    public static final String DELIVERY_EVENT_TOPIC = "net.ftgo.deliveryservice.domain.Delivery";
    
    private ChannelNames() {
        // Utility class
    }
}
