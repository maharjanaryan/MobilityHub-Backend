// com/mobilityhub/dto/request/BookingRequestDto.java
package com.mobilityhub.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequestDto {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotNull(message = "Pickup date is required")
    @Future(message = "Pickup date must be in the future")
    private LocalDateTime pickupDate;

    @NotNull(message = "Dropoff date is required")
    @Future(message = "Dropoff date must be in the future")
    private LocalDateTime dropoffDate;

    // ─────────────────────────────────────────────
    // RENTER LOCATION (ONLY LOCATION FIELD)
    // ─────────────────────────────────────────────

    private String renterLocation;
    private Double renterLatitude;
    private Double renterLongitude;

    // ─────────────────────────────────────────────
    // OTHER FIELDS
    // ─────────────────────────────────────────────

    private String insuranceType; // "standard" or "premium"
    private String paymentMethod;
    private String driverName;
    private String driverLicenseNumber;
}