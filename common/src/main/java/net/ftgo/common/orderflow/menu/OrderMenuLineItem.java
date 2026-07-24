package net.ftgo.common.orderflow.menu;

import net.ftgo.common.Money;

public class OrderMenuLineItem {

    private Long menuItemId;
    private String expectedName;
    private Money expectedUnitPrice;
    private Integer quantity;

    public OrderMenuLineItem() {
    }

    public OrderMenuLineItem(Long menuItemId, String expectedName, Money expectedUnitPrice, Integer quantity) {
        this.menuItemId = menuItemId;
        this.expectedName = expectedName;
        this.expectedUnitPrice = expectedUnitPrice;
        this.quantity = quantity;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(Long menuItemId) {
        this.menuItemId = menuItemId;
    }

    public String getExpectedName() {
        return expectedName;
    }

    public void setExpectedName(String expectedName) {
        this.expectedName = expectedName;
    }

    public Money getExpectedUnitPrice() {
        return expectedUnitPrice;
    }

    public void setExpectedUnitPrice(Money expectedUnitPrice) {
        this.expectedUnitPrice = expectedUnitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
