package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LocationResponseDto {
    private Long bookingId;
    private Long renterId;
    private String renterName;
    private Long vehicleId;
    private String vehicleName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Double speed;
    private Double heading;
    private Double accuracy;
    private LocalDateTime recordedAt;
}