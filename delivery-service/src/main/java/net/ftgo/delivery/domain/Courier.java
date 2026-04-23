package net.ftgo.delivery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Courier entity representing a delivery person.
 * 
 * Couriers can be assigned to deliveries and track their availability.
 */
@Entity
@Table(name = "couriers")
public class Courier {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Name is required")
    @Column(name = "name", nullable = false)
    private String name;
    
    @NotBlank(message = "Phone is required")
    @Column(name = "phone", nullable = false, length = 50)
    private String phone;
    
    @NotNull(message = "Available status is required")
    @Column(name = "available", nullable = false)
    private Boolean available;
    
    /**
     * Default constructor for JPA.
     */
    protected Courier() {
    }
    
    /**
     * Creates a new Courier.
     * 
     * @param name the courier's name
     * @param phone the courier's phone number
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Courier(String name, String phone) {
        validateName(name);
        validatePhone(phone);
        
        this.name = name;
        this.phone = phone;
        this.available = true;
    }
    
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
    }
    
    private void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone cannot be null or blank");
        }
    }
    
    /**
     * Marks the courier as available for deliveries.
     */
    public void markAvailable() {
        this.available = true;
    }
    
    /**
     * Marks the courier as unavailable for deliveries.
     */
    public void markUnavailable() {
        this.available = false;
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public Boolean getAvailable() {
        return available;
    }
    
    public boolean isAvailable() {
        return available != null && available;
    }
    
    @Override
    public String toString() {
        return String.format("Courier[id=%d, name=%s, phone=%s, available=%s]",
            id, name, phone, available);
    }
}
