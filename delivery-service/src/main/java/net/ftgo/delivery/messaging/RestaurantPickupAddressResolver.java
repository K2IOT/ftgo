package net.ftgo.delivery.messaging;

import net.ftgo.common.Address;

public interface RestaurantPickupAddressResolver {

    Address resolvePickupAddress(Long restaurantId);
}
