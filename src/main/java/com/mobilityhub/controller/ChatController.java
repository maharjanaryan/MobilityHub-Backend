// controller/ChatController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.request.MessageRequestDto;
import com.mobilityhub.dto.response.ConversationResponseDto;
import com.mobilityhub.dto.response.MessageResponseDto;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<MessageResponseDto> sendMessage(
            @RequestBody MessageRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        MessageResponseDto response = chatService.sendMessage(userDetails.getId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<List<ConversationResponseDto>> getConversations(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(chatService.getUserConversations(userDetails.getId()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<List<MessageResponseDto>> getMessages(
            @PathVariable Long conversationId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(chatService.getConversationMessages(conversationId, userDetails.getId()));
    }

    @PutMapping("/conversations/{conversationId}/read")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<?> markMessagesAsRead(
            @PathVariable Long conversationId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        chatService.markMessagesAsRead(conversationId, userDetails.getId());
        return ResponseEntity.ok(Map.of("message", "Messages marked as read"));
    }

    @GetMapping("/unread/count")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        long count = chatService.getUnreadCount(userDetails.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/users/search")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<?> searchUsers(@RequestParam String query) {
        // Implement user search for starting new conversations
        // Return list of users matching the query
        return ResponseEntity.ok(Map.of("message", "Search users endpoint"));
    }
}