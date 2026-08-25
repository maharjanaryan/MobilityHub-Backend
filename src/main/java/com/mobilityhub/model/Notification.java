// com/mobilityhub/model/Notification.java
package com.mobilityhub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum NotificationType {
        // KYC Related
        KYC_SUBMITTED,
        KYC_APPROVED,
        KYC_REJECTED,
        KYC_PENDING_ADMIN,

        // Vehicle Related
        VEHICLE_SUBMITTED,
        VEHICLE_APPROVED,
        VEHICLE_REJECTED,
        VEHICLE_UPDATED,

        // Booking Related
        BOOKING_REQUEST,
        BOOKING_SUBMITTED,
        BOOKING_CONFIRMED,
        BOOKING_REJECTED,
        BOOKING_CANCELLED,
        BOOKING_COMPLETED,

        // Trip Management
        TRIP_ENDED_AWAITING_CONFIRMATION,
        VEHICLE_RETURN_CONFIRMED,

        PAYMENT_RECEIVED
    }

    public enum NotificationStatus {
        READ,
        UNREAD
    }
}