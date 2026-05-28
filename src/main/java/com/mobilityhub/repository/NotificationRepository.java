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

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndStatus(Long userId, Notification.NotificationStatus status);

    long countByUserIdAndStatus(Long userId, Notification.NotificationStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.status = 'UNREAD'")
    void markAllAsRead(@Param("userId") Long userId);

    List<Notification> findByType(Notification.NotificationType type);
}