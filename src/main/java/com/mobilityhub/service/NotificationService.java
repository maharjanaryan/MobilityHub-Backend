// service/NotificationService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.response.NotificationResponseDto;
import com.mobilityhub.model.Notification;
import com.mobilityhub.model.User;
import com.mobilityhub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Create notification for user
     */
    @Transactional
    public void createNotification(User user, String title, String message,
                                   Notification.NotificationType type, Long relatedId) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .status(Notification.NotificationStatus.UNREAD)
                .relatedId(relatedId)
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for user {}: {}", user.getUsername(), title);
    }

    /**
     * Get all notifications for current user
     */
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getUserNotifications(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return notifications.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get unread notification count
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, Notification.NotificationStatus.UNREAD);
    }

    /**
     * Mark notification as read
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        notification.setStatus(Notification.NotificationStatus.READ);
        notification.setReadAt(java.time.LocalDateTime.now());
        notificationRepository.save(notification);
    }

    /**
     * Mark all notifications as read
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    /**
     * Get admin notifications (all KYC submissions)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getAdminNotifications() {
        List<Notification> notifications = notificationRepository.findByType(Notification.NotificationType.KYC_SUBMITTED);
        return notifications.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private NotificationResponseDto mapToDto(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType().name())
                .status(notification.getStatus().name())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .relatedId(notification.getRelatedId())
                .build();
    }
}