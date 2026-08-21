package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VehicleAvailabilityStatusDto {
    private Long vehicleId;
    private String vehicleName;
    private String licensePlate;
    private String brand;
    private String model;
    private Integer year;
    private Boolean isAvailable;
    private Double availabilityPercentage;
    private Integer totalUpcomingBookings;
    private List<BookingSummaryDto> upcomingBookings;
    private LocalDateTime nextBookingDate;
}