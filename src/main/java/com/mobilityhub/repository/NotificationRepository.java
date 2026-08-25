// repository/NotificationRepository.java
package com.mobilityhub.repository;

import com.mobilityhub.model.Notification;
import com.mobilityhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ─────────────────────────────────────────────
    // BASIC QUERIES
    // ─────────────────────────────────────────────

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndStatus(Long userId, Notification.NotificationStatus status);

    long countByUserIdAndStatus(Long userId, Notification.NotificationStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.status = 'UNREAD'")
    void markAllAsRead(@Param("userId") Long userId);

    List<Notification> findByType(Notification.NotificationType type);

    List<Notification> findByRelatedId(Long relatedId);

    // ─────────────────────────────────────────────
    // ADD THIS MISSING METHOD
    // ─────────────────────────────────────────────

    List<Notification> findByRelatedIdAndType(Long relatedId, Notification.NotificationType type);

    // ─────────────────────────────────────────────
    // DUPLICATE PREVENTION METHODS
    // ─────────────────────────────────────────────

    /**
     * Check if a notification exists for a user with specific type and related ID
     * created after a given timestamp (for duplicate prevention)
     */
    @Query("SELECT COUNT(n) > 0 FROM Notification n " +
            "WHERE n.user.id = :userId " +
            "AND n.type = :type " +
            "AND n.relatedId = :relatedId " +
            "AND n.createdAt > :createdAtAfter")
    boolean existsByUserIdAndTypeAndRelatedIdAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("type") Notification.NotificationType type,
            @Param("relatedId") Long relatedId,
            @Param("createdAtAfter") LocalDateTime createdAtAfter
    );

    /**
     * Check if an exact duplicate notification exists (by title, message, type, relatedId)
     * created after a given timestamp
     */
    @Query("SELECT COUNT(n) > 0 FROM Notification n " +
            "WHERE n.user.id = :userId " +
            "AND n.title = :title " +
            "AND n.message = :message " +
            "AND n.type = :type " +
            "AND n.relatedId = :relatedId " +
            "AND n.createdAt > :createdAtAfter")
    boolean existsByUserIdAndTitleAndMessageAndTypeAndRelatedIdAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("title") String title,
            @Param("message") String message,
            @Param("type") Notification.NotificationType type,
            @Param("relatedId") Long relatedId,
            @Param("createdAtAfter") LocalDateTime createdAtAfter
    );

    /**
     * Check if a notification exists for a user with specific type and related ID
     */
    boolean existsByUserIdAndTypeAndRelatedId(
            Long userId,
            Notification.NotificationType type,
            Long relatedId
    );

    /**
     * Check if a notification exists for a user with specific title, type and related ID
     */
    boolean existsByUserIdAndTitleAndTypeAndRelatedId(
            Long userId,
            String title,
            Notification.NotificationType type,
            Long relatedId
    );

    // ─────────────────────────────────────────────
    // DELETE/CLEANUP METHODS
    // ─────────────────────────────────────────────

    /**
     * Delete all notifications for a user with specific type and related ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.type = :type AND n.relatedId = :relatedId")
    void deleteByUserIdAndTypeAndRelatedId(
            @Param("userId") Long userId,
            @Param("type") Notification.NotificationType type,
            @Param("relatedId") Long relatedId
    );

    /**
     * Delete duplicate notifications (keep only the latest one per user/type/relatedId)
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE n1 FROM notifications n1 " +
            "INNER JOIN notifications n2 " +
            "WHERE n1.id > n2.id " +
            "AND n1.user_id = n2.user_id " +
            "AND n1.type = n2.type " +
            "AND n1.related_id = n2.related_id",
            nativeQuery = true)
    void deleteDuplicateNotifications();

    /**
     * Delete duplicate notifications for a specific related ID and type
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE n1 FROM notifications n1 " +
            "INNER JOIN notifications n2 " +
            "WHERE n1.id > n2.id " +
            "AND n1.user_id = n2.user_id " +
            "AND n1.type = n2.type " +
            "AND n1.related_id = n2.related_id " +
            "AND n1.related_id = :relatedId " +
            "AND n1.type = :type",
            nativeQuery = true)
    void deleteDuplicateNotificationsByRelatedIdAndType(
            @Param("relatedId") Long relatedId,
            @Param("type") String type
    );

    // ─────────────────────────────────────────────
    // COUNT METHODS
    // ─────────────────────────────────────────────

    /**
     * Count notifications for a user with specific type and related ID
     */
    long countByUserIdAndTypeAndRelatedId(
            Long userId,
            Notification.NotificationType type,
            Long relatedId
    );

    /**
     * Count notifications created after a specific time for a user
     */
    @Query("SELECT COUNT(n) FROM Notification n " +
            "WHERE n.user.id = :userId " +
            "AND n.createdAt > :since")
    long countByUserIdAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    // ─────────────────────────────────────────────
    // ADMIN METHODS
    // ─────────────────────────────────────────────

    /**
     * Get all unread notifications for a user
     */
    List<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            Notification.NotificationStatus status
    );

    /**
     * Get all notifications by type for admin
     */
    List<Notification> findByTypeOrderByCreatedAtDesc(Notification.NotificationType type);

    /**
     * Get recent notifications for a user (last 30 days)
     */
    @Query("SELECT n FROM Notification n " +
            "WHERE n.user.id = :userId " +
            "AND n.createdAt > :since " +
            "ORDER BY n.createdAt DESC")
    List<Notification> findRecentByUserId(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    /**
     * Count total unread notifications across all users (for admin)
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.status = 'UNREAD'")
    long countAllUnread();

    /**
     * Get notifications by related ID and user (for specific booking/vehicle notifications)
     */
    List<Notification> findByRelatedIdAndUserId(Long relatedId, Long userId);
}