package com.mobilityhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilityhub.dto.request.BookingActionRequestDto;
import com.mobilityhub.dto.request.BookingRequestDto;
import com.mobilityhub.dto.response.BookingResponseDto;
import com.mobilityhub.model.*;
import com.mobilityhub.repository.BookingRepository;
import com.mobilityhub.repository.OwnerKycRepository;
import com.mobilityhub.repository.UserRepository;
import com.mobilityhub.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final OwnerKycRepository ownerKycRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final BigDecimal PREMIUM_INSURANCE_COST = BigDecimal.valueOf(45);
    private static final BigDecimal STANDARD_INSURANCE_COST = BigDecimal.valueOf(22);

    private boolean hasVerifiedOwnerKyc(Long userId) {
        return ownerKycRepository.existsByUserIdAndKycStatus(userId, OwnerKyc.KycStatus.VERIFIED);
    }

    // ─────────────────────────────────────────────
    // CREATE BOOKING
    // ─────────────────────────────────────────────

    @Transactional
    public BookingResponseDto createBooking(Long renterId, BookingRequestDto request) {

        User renter = userRepository.findById(renterId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getIsAvailable()) {
            throw new RuntimeException("Vehicle is not available for booking");
        }

        LocalDate pickupLocalDate;
        LocalDate dropoffLocalDate;

        try {
            String pickupDateStr = request.getPickupDate().toString().substring(0, 10);
            String dropoffDateStr = request.getDropoffDate().toString().substring(0, 10);
            pickupLocalDate = LocalDate.parse(pickupDateStr);
            dropoffLocalDate = LocalDate.parse(dropoffDateStr);
        } catch (Exception e) {
            log.error("Failed to parse dates: pickup={}, dropoff={}",
                    request.getPickupDate(), request.getDropoffDate(), e);
            throw new RuntimeException("Invalid date format. Please select valid dates.");
        }

        LocalDateTime pickupDate = pickupLocalDate.atStartOfDay();
        LocalDateTime dropoffDate = dropoffLocalDate.atStartOfDay();

        ZoneId nepalZone = ZoneId.of("Asia/Kathmandu");
        LocalDate todayInNepal = LocalDate.now(nepalZone);
        LocalDate tomorrowInNepal = todayInNepal.plusDays(1);

        if (pickupLocalDate.isBefore(tomorrowInNepal)) {
            throw new RuntimeException("Pickup date must be at least tomorrow (Nepal time). Selected: " + pickupLocalDate);
        }
        if (!dropoffLocalDate.isAfter(pickupLocalDate)) {
            throw new RuntimeException("Dropoff date must be after pickup date");
        }
        if (bookingRepository.isVehicleBooked(vehicle.getId(), pickupDate, dropoffDate)) {
            throw new RuntimeException("Selected dates are not available. Please choose different dates.");
        }

        long days = ChronoUnit.DAYS.between(pickupDate, dropoffDate);

        if (vehicle.getMinRentalDays() != null && days < vehicle.getMinRentalDays()) {
            throw new RuntimeException("Minimum rental period is " + vehicle.getMinRentalDays() + " day(s)");
        }
        if (vehicle.getMaxRentalDays() != null && days > vehicle.getMaxRentalDays()) {
            throw new RuntimeException("Maximum rental period is " + vehicle.getMaxRentalDays() + " day(s)");
        }

        BigDecimal dailyRate = vehicle.getPricePerDay();
        BigDecimal rentalAmount = dailyRate.multiply(BigDecimal.valueOf(days));

        BigDecimal insuranceCost = BigDecimal.ZERO;
        if ("premium".equalsIgnoreCase(request.getInsuranceType())) {
            insuranceCost = PREMIUM_INSURANCE_COST.multiply(BigDecimal.valueOf(days));
        } else if ("standard".equalsIgnoreCase(request.getInsuranceType())) {
            insuranceCost = STANDARD_INSURANCE_COST.multiply(BigDecimal.valueOf(days));
        }

        BigDecimal totalAmount = rentalAmount.add(insuranceCost);

        boolean duplicatePending = bookingRepository
                .findByVehicleIdAndBookingStatusIn(vehicle.getId(), List.of(Booking.BookingStatus.PENDING))
                .stream()
                .anyMatch(b -> b.getRenter().getId().equals(renterId)
                        && b.getPickupDate().toLocalDate().equals(pickupLocalDate)
                        && b.getDropoffDate().toLocalDate().equals(dropoffLocalDate));

        if (duplicatePending) {
            throw new RuntimeException("You already have a pending booking request for these dates");
        }

        String bookingReference = generateBookingReference();

        Booking booking = Booking.builder()
                .bookingReference(bookingReference)
                .renter(renter)
                .vehicle(vehicle)
                .owner(vehicle.getOwner())
                .pickupDate(pickupDate)
                .dropoffDate(dropoffDate)
                .totalDays((int) days)
                .dailyRate(dailyRate)
                .totalAmount(totalAmount)
                .securityDeposit(vehicle.getSecurityDeposit())
                .insuranceType(request.getInsuranceType() == null ? "standard" : request.getInsuranceType().toLowerCase())
                .insuranceCost(insuranceCost)
                .paymentStatus(Booking.PaymentStatus.PENDING)
                .bookingStatus(Booking.BookingStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .driverName(request.getDriverName())
                .driverLicenseNumber(request.getDriverLicenseNumber())
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created: {}", bookingReference);

        // Notifications
        notificationService.createNotification(
                vehicle.getOwner(),
                "New Booking Request",
                renter.getFullName() + " has requested to book your vehicle " + vehicle.getBrand() + " " + vehicle.getModel(),
                Notification.NotificationType.BOOKING_REQUEST,
                saved.getId()
        );

        notificationService.createNotification(
                renter,
                "Booking Request Submitted",
                "Your booking request for " + vehicle.getBrand() + " " + vehicle.getModel() + " has been submitted.",
                Notification.NotificationType.BOOKING_SUBMITTED,
                saved.getId()
        );

        return mapToResponseDto(saved);
    }

    // ─────────────────────────────────────────────
    // APPROVE / REJECT / CANCEL BOOKING
    // ─────────────────────────────────────────────

    @Transactional
    public BookingResponseDto approveBooking(Long bookingId, Long ownerId, BookingActionRequestDto request) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to approve bookings");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Only the vehicle owner can approve this booking");
        }

        // ✅ NEW: Check if payment is completed before approving
        if (booking.getPaymentStatus() != Booking.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot approve booking. Payment has not been completed yet.");
        }

        if (booking.getBookingStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Booking has already been " + booking.getBookingStatus().name().toLowerCase());
        }

        if (bookingRepository.isVehicleBooked(
                booking.getVehicle().getId(),
                booking.getPickupDate(),
                booking.getDropoffDate())) {
            booking.setBookingStatus(Booking.BookingStatus.REJECTED);
            booking.setRejectionReason("Vehicle became unavailable for the selected dates");
            bookingRepository.save(booking);
            throw new RuntimeException("Vehicle is no longer available for the selected dates");
        }

        booking.setBookingStatus(Booking.BookingStatus.CONFIRMED);
        booking.setApprovedBy(ownerId);
        booking.setApprovedAt(LocalDateTime.now());
        booking.setOwnerNotes(request != null ? request.getOwnerNotes() : null);

        Booking saved = bookingRepository.save(booking);

        log.info("✅ Booking {} approved by owner {} after payment confirmation", bookingId, ownerId);

        notificationService.createNotification(
                booking.getRenter(),
                "🎉 Booking Confirmed!",
                "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been confirmed by the owner.",
                Notification.NotificationType.BOOKING_CONFIRMED,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    @Transactional
    public BookingResponseDto rejectBooking(Long bookingId, Long ownerId, BookingActionRequestDto request) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to reject bookings");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Only the vehicle owner can reject this booking");
        }
        if (booking.getBookingStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Booking has already been " + booking.getBookingStatus().name().toLowerCase());
        }

        booking.setBookingStatus(Booking.BookingStatus.REJECTED);
        booking.setRejectionReason(request.getRejectionReason());

        Booking saved = bookingRepository.save(booking);

        // If payment was already made, initiate refund
        if (booking.getPaymentStatus() == Booking.PaymentStatus.COMPLETED) {
            booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
            bookingRepository.save(booking);

            notificationService.createNotification(
                    booking.getRenter(),
                    "⚠️ Booking Rejected - Refund Initiated",
                    "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() +
                            " was rejected. Your payment will be refunded.",
                    Notification.NotificationType.BOOKING_REJECTED,
                    booking.getId()
            );
        }

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Rejected",
                "Your booking request for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been rejected.",
                Notification.NotificationType.BOOKING_REJECTED,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    @Transactional
    public BookingResponseDto cancelBooking(Long bookingId, Long renterId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getRenter().getId().equals(renterId)) {
            throw new RuntimeException("Only the renter can cancel this booking");
        }
        if (booking.getBookingStatus() == Booking.BookingStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed booking");
        }
        if (booking.getBookingStatus() == Booking.BookingStatus.CANCELLED) {
            throw new RuntimeException("Booking is already cancelled");
        }

        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);

        // If payment was already made, initiate refund
        if (booking.getPaymentStatus() == Booking.PaymentStatus.COMPLETED) {
            booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
        }

        Booking saved = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Cancelled",
                "Your booking has been cancelled successfully.",
                Notification.NotificationType.BOOKING_CANCELLED,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    // ─────────────────────────────────────────────
    // READ METHODS
    // ─────────────────────────────────────────────

    public BookingResponseDto getBookingById(Long bookingId, Long userId, String userRole) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        boolean isAdmin = "ROLE_ADMIN".equals(userRole) || "ADMIN".equals(userRole);
        boolean isRenter = booking.getRenter().getId().equals(userId);
        boolean isOwner = booking.getOwner().getId().equals(userId);

        if (!isAdmin && !isRenter && !isOwner) {
            throw new RuntimeException("You don't have permission to view this booking");
        }
        return mapToResponseDto(booking);
    }

    public Page<BookingResponseDto> getBookingsByRenter(Long renterId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> bookings = (status != null && !status.equalsIgnoreCase("all"))
                ? bookingRepository.findByRenterIdAndBookingStatus(
                renterId, Booking.BookingStatus.valueOf(status.toUpperCase()), pageable)
                : bookingRepository.findByRenterId(renterId, pageable);
        return bookings.map(this::mapToResponseDto);
    }

    public Page<BookingResponseDto> getBookingsByOwner(Long ownerId, String status, int page, int size) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to manage bookings");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> bookings = (status != null && !status.equalsIgnoreCase("all"))
                ? bookingRepository.findByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.valueOf(status.toUpperCase()), pageable)
                : bookingRepository.findByOwnerId(ownerId, pageable);
        return bookings.map(this::mapToResponseDto);
    }

    public List<BookingResponseDto> getPendingBookingsForOwner(Long ownerId) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to view pending bookings");
        }
        return bookingRepository
                .findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.PENDING)
                .stream().map(this::mapToResponseDto).collect(Collectors.toList());
    }

    public boolean checkAvailability(Long vehicleId, LocalDateTime pickupDate, LocalDateTime dropoffDate) {
        LocalDateTime p = pickupDate.toLocalDate().atStartOfDay();
        LocalDateTime d = dropoffDate.toLocalDate().atStartOfDay();
        return !bookingRepository.isVehicleBooked(vehicleId, p, d);
    }

    public List<LocalDateTime> getBookedDatesForVehicle(Long vehicleId) {
        List<Booking> bookings = bookingRepository.findByVehicleIdAndBookingStatusIn(
                vehicleId,
                List.of(Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.ACTIVE)
        );

        ZoneId nepalZone = ZoneId.of("Asia/Kathmandu");
        LocalDate today = LocalDate.now(nepalZone);
        Set<LocalDateTime> bookedDates = new HashSet<>();

        for (Booking b : bookings) {
            LocalDate start = b.getPickupDate().toLocalDate();
            LocalDate end = b.getDropoffDate().toLocalDate();
            LocalDate cur = start.isBefore(today) ? today : start;
            while (cur.isBefore(end)) {
                bookedDates.add(cur.atStartOfDay());
                cur = cur.plusDays(1);
            }
        }
        return new ArrayList<>(bookedDates);
    }

    // ─────────────────────────────────────────────
    // ADMIN METHODS
    // ─────────────────────────────────────────────

    public Page<BookingResponseDto> getAllBookingsForAdmin(String status, String paymentStatus, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Booking> bookingPage;

        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("all")) {
            Booking.BookingStatus bookingStatus = Booking.BookingStatus.valueOf(status.toUpperCase());
            if (paymentStatus != null && !paymentStatus.isEmpty() && !paymentStatus.equalsIgnoreCase("all")) {
                Booking.PaymentStatus paymentStatusEnum = Booking.PaymentStatus.valueOf(paymentStatus.toUpperCase());
                bookingPage = bookingRepository.findByBookingStatusAndPaymentStatus(bookingStatus, paymentStatusEnum, pageable);
            } else {
                bookingPage = bookingRepository.findByBookingStatus(bookingStatus, pageable);
            }
        } else if (paymentStatus != null && !paymentStatus.isEmpty() && !paymentStatus.equalsIgnoreCase("all")) {
            Booking.PaymentStatus paymentStatusEnum = Booking.PaymentStatus.valueOf(paymentStatus.toUpperCase());
            bookingPage = bookingRepository.findByPaymentStatus(paymentStatusEnum, pageable);
        } else {
            bookingPage = bookingRepository.findAll(pageable);
        }

        return bookingPage.map(this::mapToResponseDto);
    }

    public Map<String, Object> getBookingStats() {
        long totalBookings = bookingRepository.count();
        long pendingBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.PENDING);
        long confirmedBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.CONFIRMED);
        long activeBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.ACTIVE);
        long completedBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.COMPLETED);
        long cancelledBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.CANCELLED);
        long rejectedBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.REJECTED);

        Double totalRevenue = bookingRepository.sumTotalAmountByBookingStatusIn(
                List.of(Booking.BookingStatus.COMPLETED, Booking.BookingStatus.ACTIVE)
        );

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", totalBookings);
        stats.put("pending", pendingBookings);
        stats.put("confirmed", confirmedBookings);
        stats.put("ongoing", activeBookings);
        stats.put("completed", completedBookings);
        stats.put("cancelled", cancelledBookings);
        stats.put("rejected", rejectedBookings);
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);

        return stats;
    }

    @Transactional
    public BookingResponseDto adminApproveBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Only pending bookings can be approved");
        }

        // ✅ Check payment status
        if (booking.getPaymentStatus() != Booking.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot approve booking. Payment not completed.");
        }

        booking.setBookingStatus(Booking.BookingStatus.CONFIRMED);
        booking.setApprovedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Approved by Admin",
                "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been approved by admin.",
                Notification.NotificationType.BOOKING_CONFIRMED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    @Transactional
    public BookingResponseDto adminRejectBooking(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Only pending bookings can be rejected");
        }

        booking.setBookingStatus(Booking.BookingStatus.REJECTED);
        if (reason != null && !reason.isEmpty()) {
            booking.setRejectionReason(reason);
        }

        // If payment was already made, initiate refund
        if (booking.getPaymentStatus() == Booking.PaymentStatus.COMPLETED) {
            booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
        }

        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Rejected by Admin",
                "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been rejected by admin.",
                Notification.NotificationType.BOOKING_REJECTED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    @Transactional
    public BookingResponseDto adminCancelBooking(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() == Booking.BookingStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed booking");
        }
        if (booking.getBookingStatus() == Booking.BookingStatus.CANCELLED) {
            throw new RuntimeException("Booking is already cancelled");
        }

        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
        if (reason != null && !reason.isEmpty()) {
            booking.setRejectionReason(reason);
        }

        // If payment was already made, initiate refund
        if (booking.getPaymentStatus() == Booking.PaymentStatus.COMPLETED) {
            booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
        }

        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Cancelled by Admin",
                "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been cancelled by admin.",
                Notification.NotificationType.BOOKING_CANCELLED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    @Transactional
    public BookingResponseDto adminMarkAsOngoing(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() != Booking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed bookings can be marked as ongoing");
        }

        booking.setBookingStatus(Booking.BookingStatus.ACTIVE);
        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Trip Started!",
                "Your trip with " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has started.",
                Notification.NotificationType.BOOKING_CONFIRMED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    @Transactional
    public BookingResponseDto adminMarkAsCompleted(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() != Booking.BookingStatus.ACTIVE) {
            throw new RuntimeException("Only active bookings can be marked as completed");
        }

        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Trip Completed!",
                "Your trip with " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been completed.",
                Notification.NotificationType.BOOKING_COMPLETED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private String generateBookingReference() {
        return "BK" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String getFirstPhotoUrl(Vehicle vehicle) {
        if (vehicle.getPhotos() == null || vehicle.getPhotos().isEmpty()) return null;
        try {
            List<String> photos = objectMapper.readValue(
                    vehicle.getPhotos(), new TypeReference<List<String>>() {});
            return photos.isEmpty() ? null : photos.get(0);
        } catch (Exception e) {
            return vehicle.getPhotos();
        }
    }

    private BookingResponseDto mapToResponseDto(Booking b) {
        Vehicle v = b.getVehicle();
        return BookingResponseDto.builder()
                .id(b.getId())
                .bookingReference(b.getBookingReference())
                .vehicleId(v.getId())
                .vehicleName(v.getBrand() + " " + v.getModel())
                .vehicleImage(getFirstPhotoUrl(v))
                .ownerId(b.getOwner().getId())
                .ownerName(b.getOwner().getFullName())
                .renterId(b.getRenter().getId())
                .renterName(b.getRenter().getFullName())
                .renterEmail(b.getRenter().getEmail())
                .renterPhone(b.getRenter().getPhoneNumber())
                .pickupDate(b.getPickupDate())
                .dropoffDate(b.getDropoffDate())
                .totalDays(b.getTotalDays())
                .dailyRate(b.getDailyRate())
                .totalAmount(b.getTotalAmount())
                .securityDeposit(b.getSecurityDeposit())
                .insuranceType(b.getInsuranceType())
                .insuranceCost(b.getInsuranceCost())
                .bookingStatus(b.getBookingStatus().name())
                .paymentStatus(b.getPaymentStatus().name())
                .paymentMethod(b.getPaymentMethod())
                .rejectionReason(b.getRejectionReason())
                .ownerNotes(b.getOwnerNotes())
                .createdAt(b.getCreatedAt())
                .approvedAt(b.getApprovedAt())
                .build();
    }
}