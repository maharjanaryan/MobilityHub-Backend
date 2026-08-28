// com/mobilityhub/service/ScheduledBookingService.java
package com.mobilityhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledBookingService {

    private final BookingService bookingService;

    @Scheduled(cron = "0 0 8 * * *")
    public void sendMorningDropoffReminders() {
        log.info("🔄 Running morning dropoff reminder check at {}", LocalDateTime.now());
        try {
            bookingService.sendDropoffRemindersForToday();
        } catch (Exception e) {
            log.error("❌ Error sending dropoff reminders: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 14 * * *")
    public void sendAfternoonDropoffReminders() {
        log.info("🔄 Running afternoon dropoff reminder check at {}", LocalDateTime.now());
        try {
            bookingService.sendDropoffRemindersForToday();
        } catch (Exception e) {
            log.error("❌ Error sending afternoon dropoff reminders: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void checkLateReturns() {
        log.info("🔄 Running scheduled late return check at {}", LocalDateTime.now());
        try {
            bookingService.checkLateReturns();
        } catch (Exception e) {
            log.error("❌ Error checking late returns: {}", e.getMessage());
        }
    }
}