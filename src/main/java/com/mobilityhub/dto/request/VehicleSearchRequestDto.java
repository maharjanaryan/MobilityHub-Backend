// dto/request/VehicleSearchRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VehicleSearchRequestDto {
    private String brand;
    private String model;
    private String city;
    private String fuelType;
    private String transmission;
    private Integer minSeats;
    private Integer maxSeats;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private LocalDateTime pickupDate;
    private LocalDateTime dropoffDate;
    private Boolean isAvailable = true;
    private String sortBy;  // price_asc, price_desc, rating_desc, newest
    private Integer page = 0;
    private Integer size = 20;
}