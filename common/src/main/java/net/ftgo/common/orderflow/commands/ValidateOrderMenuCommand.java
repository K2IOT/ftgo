package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.orderflow.menu.OrderMenuLineItem;

import java.util.List;

public class ValidateOrderMenuCommand implements Command {

    private Long orderId;
    private Long restaurantId;
    private Long expectedMenuVersion;
    private List<OrderMenuLineItem> lineItems;

    public ValidateOrderMenuCommand() {
    }

    public ValidateOrderMenuCommand(Long orderId, Long restaurantId, Long expectedMenuVersion,
                                    List<OrderMenuLineItem> lineItems) {
        this.orderId = orderId;
        this.restaurantId = restaurantId;
        this.expectedMenuVersion = expectedMenuVersion;
        this.lineItems = lineItems;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public Long getExpectedMenuVersion() { return expectedMenuVersion; }
    public void setExpectedMenuVersion(Long expectedMenuVersion) { this.expectedMenuVersion = expectedMenuVersion; }
    public List<OrderMenuLineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<OrderMenuLineItem> lineItems) { this.lineItems = lineItems; }
}
