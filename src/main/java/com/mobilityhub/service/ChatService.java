// service/ChatService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.request.MessageRequestDto;
import com.mobilityhub.dto.response.ConversationResponseDto;
import com.mobilityhub.dto.response.MessageResponseDto;
import com.mobilityhub.model.Conversation;
import com.mobilityhub.model.Message;
import com.mobilityhub.model.User;
import com.mobilityhub.repository.ConversationRepository;
import com.mobilityhub.repository.MessageRepository;
import com.mobilityhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public MessageResponseDto sendMessage(Long senderId, MessageRequestDto request) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        // Find or create conversation
        Conversation conversation = conversationRepository
                .findConversationBetweenUsers(senderId, request.getReceiverId())
                .orElseGet(() -> {
                    Conversation newConversation = Conversation.builder()
                            .participant1(sender)
                            .participant2(receiver)
                            .build();
                    return conversationRepository.save(newConversation);
                });

        // Create message
        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .receiver(receiver)
                .message(request.getMessage())
                .isRead(false)
                .build();

        Message savedMessage = messageRepository.save(message);

        // Update conversation last message
        conversation.setLastMessage(request.getMessage());
        conversation.setLastMessageTime(LocalDateTime.now());
        conversationRepository.save(conversation);

        // Create response DTO
        MessageResponseDto response = mapToMessageDto(savedMessage);

        // Send to receiver via WebSocket
        String destination = "/queue/messages/" + receiver.getId();
        messagingTemplate.convertAndSendToUser(
                receiver.getId().toString(),
                "/queue/messages",
                response
        );

        // Also send to sender for confirmation
        messagingTemplate.convertAndSendToUser(
                sender.getId().toString(),
                "/queue/messages",
                response
        );

        log.info("Message sent from {} to {}", sender.getUsername(), receiver.getUsername());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponseDto> getUserConversations(Long userId) {
        List<Conversation> conversations = conversationRepository.findConversationsByUserId(userId);

        return conversations.stream()
                .map(conv -> {
                    Long otherUserId = conv.getParticipant1().getId().equals(userId)
                            ? conv.getParticipant2().getId()
                            : conv.getParticipant1().getId();

                    User otherUser = userRepository.findById(otherUserId).orElse(null);

                    long unreadCount = messageRepository.countUnreadMessagesInConversation(conv.getId(), userId);

                    return ConversationResponseDto.builder()
                            .id(conv.getId())
                            .otherUserId(otherUserId)
                            .otherUserName(otherUser != null ? otherUser.getUsername() : "Unknown")
                            .lastMessage(conv.getLastMessage())
                            .lastMessageTime(conv.getLastMessageTime())
                            .unreadCount(unreadCount)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MessageResponseDto> getConversationMessages(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository
                .findConversationForUser(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        return messages.stream()
                .map(this::mapToMessageDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markMessagesAsRead(Long conversationId, Long userId) {
        messageRepository.markMessagesAsRead(conversationId, userId);

        // Notify sender that messages were read
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        Long otherUserId = conversation.getParticipant1().getId().equals(userId)
                ? conversation.getParticipant2().getId()
                : conversation.getParticipant1().getId();

        messagingTemplate.convertAndSendToUser(
                otherUserId.toString(),
                "/queue/messages/read",
                conversationId
        );
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return messageRepository.countUnreadMessagesByUserId(userId);
    }

    private MessageResponseDto mapToMessageDto(Message message) {
        return MessageResponseDto.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getUsername())
                .receiverId(message.getReceiver().getId())
                .receiverName(message.getReceiver().getUsername())
                .message(message.getMessage())
                .isRead(message.isRead())
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}