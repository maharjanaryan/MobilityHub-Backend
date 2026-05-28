// dto/request/KycVerificationRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class KycVerificationRequestDto {

    @NotNull(message = "Approval status is required")
    private Boolean approved;

    private String rejectionReason;  // Required if approved = false

    private String adminNotes;  // Optional internal notes
}


