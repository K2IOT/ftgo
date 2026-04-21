package net.ftgo.restaurant.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Address;

import java.time.LocalDateTime;

/**
 * Restaurant aggregate representing a restaurant profile with menu management.
 * 
 * Manages restaurant information including name, address, opening hours, and menu items.
 */
@Entity
@Table(name = "restaurants")
public class Restaurant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Restaurant name is required")
    @Column(nullable = false)
    private String name;
    
    @NotNull(message = "Address is required")
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "address_street", nullable = false)),
        @AttributeOverride(name = "city", column = @Column(name = "address_city", nullable = false)),
        @AttributeOverride(name = "state", column = @Column(name = "address_state", nullable = false)),
        @AttributeOverride(name = "zipCode", column = @Column(name = "address_zip_code", nullable = false))
    })
    private Address address;
    
    @NotNull(message = "Opening hours are required")
    @Column(name = "opening_hours", nullable = false, columnDefinition = "JSON")
    private String openingHours;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Default constructor for JPA.
     */
    protected Restaurant() {
    }
    
    /**
     * Creates a new Restaurant with the specified details.
     * 
     * @param name the restaurant's name
     * @param address the restaurant's address
     * @param openingHours the restaurant's opening hours in JSON format
     * @throws IllegalArgumentException if any required field is null or blank
     */
    public Restaurant(String name, Address address, String openingHours) {
        validateName(name);
        validateAddress(address);
        validateOpeningHours(openingHours);
        
        this.name = name;
        this.address = address;
        this.openingHours = openingHours;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Validates that the restaurant name is not null or blank.
     * 
     * @param name the name to validate
     * @throws IllegalArgumentException if name is null or blank
     */
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Restaurant name cannot be null or blank");
        }
    }
    
    /**
     * Validates that the address is not null.
     * 
     * @param address the address to validate
     * @throws IllegalArgumentException if address is null
     */
    private void validateAddress(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }
    }
    
    /**
     * Validates that the opening hours are not null or blank.
     * 
     * @param openingHours the opening hours to validate
     * @throws IllegalArgumentException if openingHours is null or blank
     */
    private void validateOpeningHours(String openingHours) {
        if (openingHours == null || openingHours.isBlank()) {
            throw new IllegalArgumentException("Opening hours cannot be null or blank");
        }
    }
    
    /**
     * Updates the restaurant's profile information.
     * 
     * @param name the new name (optional)
     * @param address the new address (optional)
     * @param openingHours the new opening hours (optional)
     */
    public void updateProfile(String name, Address address, String openingHours) {
        if (name != null && !name.isBlank()) {
            validateName(name);
            this.name = name;
        }
        if (address != null) {
            this.address = address;
        }
        if (openingHours != null && !openingHours.isBlank()) {
            this.openingHours = openingHours;
        }
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public Address getAddress() {
        return address;
    }
    
    public String getOpeningHours() {
        return openingHours;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
