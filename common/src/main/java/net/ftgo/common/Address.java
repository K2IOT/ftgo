package net.ftgo.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import net.ftgo.common.web.RequestLimits;

import java.util.Objects;

/** Value object representing a physical address. */
@Embeddable
public class Address {

    @NotBlank
    @Size(max = RequestLimits.MAX_ADDRESS_LINE_LENGTH)
    private String street;

    @NotBlank
    @Size(max = RequestLimits.MAX_CITY_OR_STATE_LENGTH)
    private String city;

    @NotBlank
    @Size(max = RequestLimits.MAX_CITY_OR_STATE_LENGTH)
    private String state;

    @NotBlank
    @Size(max = RequestLimits.MAX_POSTAL_CODE_LENGTH)
    private String zipCode;

    protected Address() {
    }

    @JsonCreator
    public Address(
        @JsonProperty("street") String street,
        @JsonProperty("city") String city,
        @JsonProperty("state") String state,
        @JsonProperty("zipCode") String zipCode
    ) {
        if (street == null || street.isBlank()) {
            throw new IllegalArgumentException("Street cannot be null or blank");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City cannot be null or blank");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("State cannot be null or blank");
        }
        if (zipCode == null || zipCode.isBlank()) {
            throw new IllegalArgumentException("Zip code cannot be null or blank");
        }
        this.street = street;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
    }

    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getZipCode() { return zipCode; }

    @JsonIgnore
    public String getFullAddress() {
        return String.format("%s, %s, %s %s", street, city, state, zipCode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(street, address.street)
            && Objects.equals(city, address.city)
            && Objects.equals(state, address.state)
            && Objects.equals(zipCode, address.zipCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, city, state, zipCode);
    }

    @Override
    public String toString() {
        return getFullAddress();
    }
}
