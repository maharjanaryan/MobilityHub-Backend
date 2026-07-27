// dto/request/MessageRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;

@Data
public class MessageRequestDto {
    private Long receiverId;
    private String message;
}
