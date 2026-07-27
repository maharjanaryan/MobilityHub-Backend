// dto/response/ConversationResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationResponseDto {
    private Long id;
    private Long otherUserId;
    private String otherUserName;
    private String otherUserAvatar;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private long unreadCount;
    private List<MessageResponseDto> messages;
}