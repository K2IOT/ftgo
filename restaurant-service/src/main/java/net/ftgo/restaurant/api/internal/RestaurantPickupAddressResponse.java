package net.ftgo.restaurant.api.internal;

import net.ftgo.common.Address;

public record RestaurantPickupAddressResponse(Long restaurantId, Address address) {
}
