// dto/response/UserStatisticsDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStatisticsDto {
    private long totalUsers;
    private long activeUsers;
    private long adminUsers;
    private long ownerUsers;
    private long regularUsers;
    private long oAuthUsers;
    private long verifiedUsers;
}