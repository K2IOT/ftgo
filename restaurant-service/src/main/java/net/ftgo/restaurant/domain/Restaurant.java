package net.ftgo.restaurant.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Address;

import java.time.LocalDateTime;

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

    @Column(name = "menu_version", nullable = false)
    private Long menuVersion = 0L;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "accepting_orders", nullable = false)
    private Boolean acceptingOrders = true;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Restaurant() {
    }

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

    private void validateName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Restaurant name cannot be null or blank");
        }
    }

    private void validateAddress(Address value) {
        if (value == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }
    }

    private void validateOpeningHours(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Opening hours cannot be null or blank");
        }
    }

    public void updateProfile(String name, Address address, String openingHours) {
        if (name != null && !name.isBlank()) {
            validateName(name);
            this.name = name;
        }
        if (address != null) {
            this.address = address;
        }
        if (openingHours != null && !openingHours.isBlank()) {
            validateOpeningHours(openingHours);
            this.openingHours = openingHours;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementMenuVersion() {
        this.menuVersion = this.menuVersion + 1;
        this.updatedAt = LocalDateTime.now();
    }

    public void enable() { this.enabled = true; }
    public void disable() { this.enabled = false; }
    public void openForOrders() { this.acceptingOrders = true; }
    public void closeForOrders() { this.acceptingOrders = false; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Address getAddress() { return address; }
    public String getOpeningHours() { return openingHours; }
    public Long getMenuVersion() { return menuVersion; }
    public boolean isEnabled() { return Boolean.TRUE.equals(enabled); }
    public boolean isAcceptingOrders() { return Boolean.TRUE.equals(acceptingOrders); }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @PrePersist
    protected void onCreate() {
        if (menuVersion == null) menuVersion = 0L;
        if (enabled == null) enabled = true;
        if (acceptingOrders == null) acceptingOrders = true;
        if (version == null) version = 0L;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
