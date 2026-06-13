package com.mobilityhub.dto.request;

import lombok.Data;

@Data
public class BookingRequestDto {
    private Long vehicleId;
    private String pickupDate;   // accept as String, parse date-only in service
    private String dropoffDate;  // e.g. "2026-06-13T00:00:00.000Z" → LocalDate 2026-06-13
    private String insuranceType;
    private String paymentMethod;
    private String driverName;
    private String driverLicenseNumber;
}