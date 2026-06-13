// dto/request/BookingActionRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;

@Data
public class BookingActionRequestDto {
    private String action;  // APPROVE, REJECT, CANCEL, COMPLETE
    private String rejectionReason;
    private String ownerNotes;
}