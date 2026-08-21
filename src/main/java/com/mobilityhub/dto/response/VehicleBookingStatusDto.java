package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VehicleBookingStatusDto {
    private Long vehicleId;
    private String vehicleName;
    private String licensePlate;
    private String brand;
    private String model;
    private Integer year;
    private Boolean isCurrentlyBooked;
    private List<BookingSummaryDto> activeBookings;
    private LocalDateTime nextAvailableDate;
    private Integer totalActiveBookings;
}