// dto/response/MessageResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponseDto {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private String message;
    private boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}