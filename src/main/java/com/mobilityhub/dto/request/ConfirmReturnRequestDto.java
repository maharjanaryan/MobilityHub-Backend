// com/mobilityhub/dto/request/ConfirmReturnRequestDto.java
package com.mobilityhub.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmReturnRequestDto {
    private Boolean vehicleDamaged;
    private String damageNotes;
}