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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // Lock map to prevent concurrent duplicate notifications
    private final ConcurrentHashMap<String, Object> notificationLocks = new ConcurrentHashMap<>();

    /**
     * Create notification for a single user with duplicate prevention
     */
    @Transactional
    public void createNotification(User user, String title, String message,
                                   Notification.NotificationType type, Long relatedId) {

        // Create a unique key for this notification
        String lockKey = user.getId() + "_" + type.name() + "_" + relatedId;

        synchronized (notificationLocks.computeIfAbsent(lockKey, k -> new Object())) {
            try {
                // Check for duplicate notification within last 10 seconds
                LocalDateTime checkTime = LocalDateTime.now().minusSeconds(10);

                // Check if a similar notification already exists recently
                boolean duplicateExists = notificationRepository.existsByUserIdAndTypeAndRelatedIdAndCreatedAtAfter(
                        user.getId(),
                        type,
                        relatedId,
                        checkTime
                );

                if (duplicateExists) {
                    log.info("Duplicate notification prevented for user {}: {}", user.getUsername(), title);
                    return;
                }

                // Also check by exact title and message for extra safety
                boolean exactDuplicateExists = notificationRepository.existsByUserIdAndTitleAndMessageAndTypeAndRelatedIdAndCreatedAtAfter(
                        user.getId(),
                        title,
                        message,
                        type,
                        relatedId,
                        checkTime
                );

                if (exactDuplicateExists) {
                    log.info("Exact duplicate notification prevented for user {}: {}", user.getUsername(), title);
                    return;
                }

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

            } finally {
                // Clean up the lock after a delay
                cleanupLock(lockKey);
            }
        }
    }

    /**
     * Create notification for multiple users with duplicate prevention
     */
    @Transactional
    public void createNotificationForUsers(List<User> users, String title, String message,
                                           Notification.NotificationType type, Long relatedId) {
        if (users == null || users.isEmpty()) {
            return;
        }

        List<Notification> notificationsToSave = new ArrayList<>();
        LocalDateTime checkTime = LocalDateTime.now().minusSeconds(10);

        for (User user : users) {
            // Check for duplicate for each user
            boolean duplicateExists = notificationRepository.existsByUserIdAndTitleAndMessageAndTypeAndRelatedIdAndCreatedAtAfter(
                    user.getId(),
                    title,
                    message,
                    type,
                    relatedId,
                    checkTime
            );

            if (!duplicateExists) {
                notificationsToSave.add(
                        Notification.builder()
                                .user(user)
                                .title(title)
                                .message(message)
                                .type(type)
                                .status(Notification.NotificationStatus.UNREAD)
                                .relatedId(relatedId)
                                .build()
                );
            }
        }

        if (!notificationsToSave.isEmpty()) {
            notificationRepository.saveAll(notificationsToSave);
            log.info("Created {} new notifications for {} users", notificationsToSave.size(), users.size());
        } else {
            log.info("All notifications were duplicates, skipped saving");
        }
    }

    /**
     * Clean up the lock after a delay
     */
    private void cleanupLock(String lockKey) {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                notificationLocks.remove(lockKey);
            }
        }).start();
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

    /**
     * Delete duplicate notifications for cleanup
     */
    @Transactional
    public void deleteDuplicateNotifications(Long relatedId, Notification.NotificationType type) {
        List<Notification> notifications = notificationRepository.findByRelatedIdAndType(relatedId, type);

        if (notifications.size() <= 1) {
            return;
        }

        // Keep the first one, delete the rest
        Notification first = notifications.get(0);
        for (int i = 1; i < notifications.size(); i++) {
            notificationRepository.delete(notifications.get(i));
        }

        log.info("Deleted {} duplicate notifications for relatedId: {}, type: {}",
                notifications.size() - 1, relatedId, type);
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