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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Create notification for a single user
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
     * Create notification for multiple users
     */
    @Transactional
    public void createNotificationForUsers(List<User> users, String title, String message,
                                           Notification.NotificationType type, Long relatedId) {
        if (users == null || users.isEmpty()) {
            return;
        }

        List<Notification> notifications = users.stream()
                .map(user -> Notification.builder()
                        .user(user)
                        .title(title)
                        .message(message)
                        .type(type)
                        .status(Notification.NotificationStatus.UNREAD)
                        .relatedId(relatedId)
                        .build())
                .collect(Collectors.toList());

        notificationRepository.saveAll(notifications);
        log.info("Created {} notifications for {} users", notifications.size(), users.size());
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
        notification.setReadAt(LocalDateTime.now());
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
     * Get admin notifications (all KYC submissions and vehicle submissions)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getAdminNotifications() {
        List<Notification> notifications = new ArrayList<>();

        // Get KYC notifications
        notifications.addAll(notificationRepository.findByType(Notification.NotificationType.KYC_SUBMITTED));
        notifications.addAll(notificationRepository.findByType(Notification.NotificationType.KYC_PENDING_ADMIN));

        // Get Vehicle notifications
        notifications.addAll(notificationRepository.findByType(Notification.NotificationType.VEHICLE_SUBMITTED));

        // Sort by createdAt descending (newest first)
        notifications.sort((n1, n2) -> n2.getCreatedAt().compareTo(n1.getCreatedAt()));

        return notifications.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get notifications by related ID (for debugging)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getNotificationsByRelatedId(Long relatedId) {
        List<Notification> notifications = notificationRepository.findByRelatedId(relatedId);
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