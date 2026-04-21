package net.ftgo.restaurant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Address;

/**
 * Request DTO for creating a new restaurant.
 */
public class CreateRestaurantRequest {
    
    @NotBlank(message = "Restaurant name is required")
    private String name;
    
    @NotNull(message = "Address is required")
    private Address address;
    
    @NotBlank(message = "Opening hours are required")
    private String openingHours;
    
    public CreateRestaurantRequest() {
    }
    
    public CreateRestaurantRequest(String name, Address address, String openingHours) {
        this.name = name;
        this.address = address;
        this.openingHours = openingHours;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Address getAddress() {
        return address;
    }
    
    public void setAddress(Address address) {
        this.address = address;
    }
    
    public String getOpeningHours() {
        return openingHours;
    }
    
    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }
}
