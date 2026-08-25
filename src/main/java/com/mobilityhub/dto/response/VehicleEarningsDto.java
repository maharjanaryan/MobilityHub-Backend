// com/mobilityhub/dto/response/VehicleEarningsDto.java
package com.mobilityhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleEarningsDto {
    private Long vehicleId;
    private String vehicleName;
    private String brand;
    private String model;
    private BigDecimal totalEarnings;
    private Integer totalBookings;
    private BigDecimal averagePerBooking;
}