// com/mobilityhub/service/EarningsService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.response.EarningsSummaryDto;
import com.mobilityhub.dto.response.EarningsTransactionDto;
import com.mobilityhub.dto.response.MonthlyEarningsDto;
import com.mobilityhub.dto.response.VehicleEarningsDto;
import com.mobilityhub.model.Booking;
import com.mobilityhub.model.Role;
import com.mobilityhub.model.User;
import com.mobilityhub.model.WalletTransaction;
import com.mobilityhub.repository.BookingRepository;
import com.mobilityhub.repository.UserRepository;
import com.mobilityhub.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarningsService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * Check if user has ADMIN role
     */
    private boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        return user.getRole() != null && user.getRole() == Role.ADMIN;
    }

    /**
     * Get earnings summary - works for both owners and admin
     */
    public EarningsSummaryDto getEarningsSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (isAdmin(user)) {
            log.info("Admin {} fetching platform earnings summary", user.getUsername());
            return getAdminCommissionSummary();
        } else {
            log.info("Owner {} fetching earnings summary", user.getUsername());
            return getOwnerEarningsSummary(userId);
        }
    }

    /**
     * Get earnings transactions - works for both owners and admin
     */
    public Page<EarningsTransactionDto> getEarningsTransactions(Long userId, String type, String status,
                                                                String startDate, String endDate, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (isAdmin(user)) {
            return getAdminEarningsTransactions(type, status, startDate, endDate, pageable);
        } else {
            return getOwnerEarningsTransactions(userId, type, status, startDate, endDate, pageable);
        }
    }

    /**
     * Get monthly earnings - works for both owners and admin
     */
    public List<MonthlyEarningsDto> getMonthlyEarnings(Long userId, int year) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (isAdmin(user)) {
            return getAdminMonthlyEarnings(year);
        } else {
            return getOwnerMonthlyEarnings(userId, year);
        }
    }

    /**
     * Get vehicle earnings - only for owners
     */
    public List<VehicleEarningsDto> getVehicleEarnings(Long ownerId) {
        User user = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (isAdmin(user)) {
            log.warn("Admin requested vehicle earnings - returning empty list");
            return new ArrayList<>();
        }

        return getOwnerVehicleEarnings(ownerId);
    }

    /**
     * Get chart data - works for both owners and admin
     */
    public Map<String, Object> getChartData(Long userId, String period) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (isAdmin(user)) {
            return getAdminChartData(period);
        } else {
            return getOwnerChartData(userId, period);
        }
    }

    /**
     * Export earnings - works for both owners and admin
     */
    public ResponseEntity<byte[]> exportEarnings(Long userId, String startDate, String endDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (isAdmin(user)) {
            return exportAdminEarnings(startDate, endDate);
        } else {
            return exportOwnerEarnings(userId, startDate, endDate);
        }
    }

    // ============ ADMIN COMMISSION METHODS (Simple & Direct) ============

    /**
     * SIMPLE: Get admin commission from wallet transactions
     * This reads directly from WalletTransaction table
     */
    public EarningsSummaryDto getAdminCommissionSummary() {
        // Get all wallet transactions
        List<WalletTransaction> allTransactions = walletTransactionRepository.findAll();

        log.info("Total wallet transactions found: {}", allTransactions.size());

        // Find commission transactions (CREDIT with "service fee" in description)
        List<WalletTransaction> commissionTransactions = allTransactions.stream()
                .filter(wt -> wt.getType() == WalletTransaction.TransactionType.CREDIT)
                .filter(wt -> wt.getDescription() != null &&
                        wt.getDescription().toLowerCase().contains("service fee"))
                .collect(Collectors.toList());

        log.info("Found {} commission transactions", commissionTransactions.size());

        // Log each commission transaction for debugging
        for (WalletTransaction wt : commissionTransactions) {
            log.info("Commission: amount={}, description={}, date={}",
                    wt.getAmount(), wt.getDescription(), wt.getTransactionDate());
        }

        // Calculate total commission
        double totalCommission = commissionTransactions.stream()
                .mapToDouble(WalletTransaction::getAmount)
                .sum();

        // Get total completed bookings
        long totalBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.COMPLETED);

        // Calculate average commission per booking
        double avgCommission = totalBookings > 0 ? totalCommission / totalBookings : 0;

        log.info("Admin Commission Summary: totalCommission={}, totalBookings={}, avgCommission={}",
                totalCommission, totalBookings, avgCommission);

        return EarningsSummaryDto.builder()
                .totalEarnings(BigDecimal.valueOf(totalCommission))
                .currentMonthEarnings(BigDecimal.valueOf(totalCommission))
                .currentWeekEarnings(BigDecimal.valueOf(totalCommission))
                .pendingPayout(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .totalBookings((int) totalBookings)
                .completedBookings((int) totalBookings)
                .averageRating(0.0)
                .availableBalance(BigDecimal.valueOf(totalCommission))
                .build();
    }

    private Page<EarningsTransactionDto> getAdminEarningsTransactions(String type, String status,
                                                                      String startDate, String endDate,
                                                                      Pageable pageable) {
        List<WalletTransaction> allTransactions = walletTransactionRepository.findAll();

        List<WalletTransaction> commissionTransactions = allTransactions.stream()
                .filter(wt -> wt.getType() == WalletTransaction.TransactionType.CREDIT)
                .filter(wt -> wt.getDescription() != null &&
                        wt.getDescription().toLowerCase().contains("service fee"))
                .collect(Collectors.toList());

        List<EarningsTransactionDto> transactions = commissionTransactions.stream()
                .map(wt -> EarningsTransactionDto.builder()
                        .id(wt.getId())
                        .amount(BigDecimal.valueOf(wt.getAmount()))
                        .type("COMMISSION")
                        .status("COMPLETED")
                        .transactionDate(wt.getTransactionDate())
                        .description(wt.getDescription() != null ? wt.getDescription() : "Platform commission")
                        .build())
                .collect(Collectors.toList());

        // Apply filters
        if (type != null && !type.isEmpty()) {
            transactions = transactions.stream()
                    .filter(t -> t.getType().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.isEmpty()) {
            transactions = transactions.stream()
                    .filter(t -> t.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        // Date filtering
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        if (startDate != null && !startDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate, formatter);
            transactions = transactions.stream()
                    .filter(t -> t.getTransactionDate() != null &&
                            t.getTransactionDate().toLocalDate().isAfter(start.minusDays(1)))
                    .collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDate end = LocalDate.parse(endDate, formatter);
            transactions = transactions.stream()
                    .filter(t -> t.getTransactionDate() != null &&
                            t.getTransactionDate().toLocalDate().isBefore(end.plusDays(1)))
                    .collect(Collectors.toList());
        }

        transactions.sort((a, b) -> {
            if (a.getTransactionDate() == null) return 1;
            if (b.getTransactionDate() == null) return -1;
            return b.getTransactionDate().compareTo(a.getTransactionDate());
        });

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), transactions.size());

        List<EarningsTransactionDto> paginatedList = new ArrayList<>();
        if (start < transactions.size()) {
            paginatedList = transactions.subList(start, end);
        }

        return new PageImpl<>(paginatedList, pageable, transactions.size());
    }

    private List<MonthlyEarningsDto> getAdminMonthlyEarnings(int year) {
        List<MonthlyEarningsDto> monthlyData = new ArrayList<>();

        List<WalletTransaction> allTransactions = walletTransactionRepository.findAll();

        List<WalletTransaction> commissionTransactions = allTransactions.stream()
                .filter(wt -> wt.getType() == WalletTransaction.TransactionType.CREDIT)
                .filter(wt -> wt.getDescription() != null &&
                        wt.getDescription().toLowerCase().contains("service fee"))
                .collect(Collectors.toList());

        for (int month = 1; month <= 12; month++) {
            LocalDateTime startOfMonth = LocalDate.of(year, month, 1).atStartOfDay();
            LocalDateTime endOfMonth = LocalDate.of(year, month, 1)
                    .withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())
                    .atTime(23, 59, 59);

            List<WalletTransaction> monthTransactions = commissionTransactions.stream()
                    .filter(wt -> wt.getTransactionDate() != null &&
                            wt.getTransactionDate().isAfter(startOfMonth) &&
                            wt.getTransactionDate().isBefore(endOfMonth))
                    .collect(Collectors.toList());

            BigDecimal commission = monthTransactions.stream()
                    .map(wt -> BigDecimal.valueOf(wt.getAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            monthlyData.add(MonthlyEarningsDto.builder()
                    .month(getMonthName(month))
                    .year(year)
                    .earnings(commission)
                    .bookingCount(monthTransactions.size())
                    .build());
        }

        return monthlyData;
    }

    private Map<String, Object> getAdminChartData(String period) {
        List<WalletTransaction> allTransactions = walletTransactionRepository.findAll();

        List<WalletTransaction> commissionTransactions = allTransactions.stream()
                .filter(wt -> wt.getType() == WalletTransaction.TransactionType.CREDIT)
                .filter(wt -> wt.getDescription() != null &&
                        wt.getDescription().toLowerCase().contains("service fee"))
                .collect(Collectors.toList());

        Map<String, Object> chartData = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Double> earnings = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        int monthsToShow = period.equals("3months") ? 3 : period.equals("12months") ? 12 : 6;
        LocalDate startDate = LocalDate.now().minusMonths(monthsToShow - 1);

        for (int i = 0; i < monthsToShow; i++) {
            LocalDate currentDate = startDate.plusMonths(i);
            String monthLabel = currentDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            labels.add(monthLabel);

            LocalDateTime startOfMonth = currentDate.withDayOfMonth(1).atStartOfDay();
            LocalDateTime endOfMonth = currentDate.withDayOfMonth(currentDate.lengthOfMonth())
                    .atTime(23, 59, 59);

            List<WalletTransaction> monthTransactions = commissionTransactions.stream()
                    .filter(wt -> wt.getTransactionDate() != null &&
                            wt.getTransactionDate().isAfter(startOfMonth) &&
                            wt.getTransactionDate().isBefore(endOfMonth))
                    .collect(Collectors.toList());

            double monthCommission = monthTransactions.stream()
                    .mapToDouble(WalletTransaction::getAmount)
                    .sum();

            earnings.add(monthCommission);
            counts.add(monthTransactions.size());
        }

        chartData.put("labels", labels);
        chartData.put("earnings", earnings);
        chartData.put("counts", counts);

        return chartData;
    }

    private ResponseEntity<byte[]> exportAdminEarnings(String startDate, String endDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Description,Amount,Reference ID\n");

        List<WalletTransaction> allTransactions = walletTransactionRepository.findAll();

        List<WalletTransaction> commissionTransactions = allTransactions.stream()
                .filter(wt -> wt.getType() == WalletTransaction.TransactionType.CREDIT)
                .filter(wt -> wt.getDescription() != null &&
                        wt.getDescription().toLowerCase().contains("service fee"))
                .collect(Collectors.toList());

        for (WalletTransaction wt : commissionTransactions) {
            csv.append(wt.getTransactionDate() != null ?
                            wt.getTransactionDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                    .append(",")
                    .append(wt.getDescription() != null ? wt.getDescription() : "Commission")
                    .append(",")
                    .append(wt.getAmount())
                    .append(",")
                    .append(wt.getReferenceId() != null ? wt.getReferenceId() : "")
                    .append("\n");
        }

        byte[] csvBytes = csv.toString().getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "text/csv");
        headers.add("Content-Disposition", "attachment; filename=admin_commission_export.csv");

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    // ============ OWNER SPECIFIC METHODS ============

    private EarningsSummaryDto getOwnerEarningsSummary(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Booking> completedBookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.COMPLETED);
        List<Booking> confirmedBookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.CONFIRMED);

        BigDecimal totalEarnings = completedBookings.stream()
                .map(b -> {
                    BigDecimal rental = b.getRentalAmount() != null ? b.getRentalAmount() : BigDecimal.ZERO;
                    BigDecimal insurance = b.getInsuranceFee() != null ? b.getInsuranceFee() : BigDecimal.ZERO;
                    return rental.add(insurance);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal currentMonthEarnings = completedBookings.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isAfter(startOfMonth))
                .map(b -> {
                    BigDecimal rental = b.getRentalAmount() != null ? b.getRentalAmount() : BigDecimal.ZERO;
                    BigDecimal insurance = b.getInsuranceFee() != null ? b.getInsuranceFee() : BigDecimal.ZERO;
                    return rental.add(insurance);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime startOfWeek = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay();
        BigDecimal currentWeekEarnings = completedBookings.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isAfter(startOfWeek))
                .map(b -> {
                    BigDecimal rental = b.getRentalAmount() != null ? b.getRentalAmount() : BigDecimal.ZERO;
                    BigDecimal insurance = b.getInsuranceFee() != null ? b.getInsuranceFee() : BigDecimal.ZERO;
                    return rental.add(insurance);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingPayout = confirmedBookings.stream()
                .filter(b -> b.getPaymentStatus() == Booking.PaymentStatus.COMPLETED)
                .map(b -> {
                    BigDecimal rental = b.getRentalAmount() != null ? b.getRentalAmount() : BigDecimal.ZERO;
                    BigDecimal insurance = b.getInsuranceFee() != null ? b.getInsuranceFee() : BigDecimal.ZERO;
                    return rental.add(insurance);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<WalletTransaction> withdrawals = walletTransactionRepository.findByUserIdAndType(ownerId, WalletTransaction.TransactionType.DEBIT);
        BigDecimal totalWithdrawn = withdrawals.stream()
                .map(wt -> BigDecimal.valueOf(wt.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalBookings = completedBookings.size();
        int completedBookingsCount = completedBookings.size();

        double averageRating = completedBookings.stream()
                .filter(b -> b.getVehicle() != null && b.getVehicle().getAverageRating() != null)
                .mapToDouble(b -> b.getVehicle().getAverageRating().doubleValue())
                .average()
                .orElse(0.0);

        BigDecimal availableBalance = totalEarnings.subtract(totalWithdrawn).subtract(pendingPayout);

        return EarningsSummaryDto.builder()
                .totalEarnings(totalEarnings)
                .currentMonthEarnings(currentMonthEarnings)
                .currentWeekEarnings(currentWeekEarnings)
                .pendingPayout(pendingPayout)
                .totalWithdrawn(totalWithdrawn)
                .totalBookings(totalBookings)
                .completedBookings(completedBookingsCount)
                .averageRating(averageRating)
                .availableBalance(availableBalance)
                .build();
    }

    private Page<EarningsTransactionDto> getOwnerEarningsTransactions(Long ownerId, String type, String status,
                                                                      String startDate, String endDate, Pageable pageable) {
        List<EarningsTransactionDto> transactions = new ArrayList<>();

        List<Booking> bookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.COMPLETED);

        for (Booking booking : bookings) {
            Double totalOwnerEarnings = 0.0;
            if (booking.getRentalAmount() != null) {
                totalOwnerEarnings += booking.getRentalAmount().doubleValue();
            }
            if (booking.getInsuranceFee() != null) {
                totalOwnerEarnings += booking.getInsuranceFee().doubleValue();
            }

            if (totalOwnerEarnings > 0) {
                transactions.add(EarningsTransactionDto.builder()
                        .id(booking.getId())
                        .bookingId(booking.getId())
                        .bookingReference(booking.getBookingReference())
                        .vehicleName(booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel())
                        .renterName(booking.getRenter().getFullName())
                        .amount(BigDecimal.valueOf(totalOwnerEarnings))
                        .type("RENTAL")
                        .status("COMPLETED")
                        .transactionDate(booking.getPaymentVerifiedAt() != null ?
                                booking.getPaymentVerifiedAt() : booking.getCreatedAt())
                        .description("Rental + Insurance payment for booking: " + booking.getBookingReference())
                        .build());
            }
        }

        List<WalletTransaction> walletTransactions = walletTransactionRepository.findByUserIdOrderByTransactionDateDesc(ownerId);

        for (WalletTransaction wt : walletTransactions) {
            if (wt.getType() == WalletTransaction.TransactionType.CREDIT) {
                continue;
            }

            transactions.add(EarningsTransactionDto.builder()
                    .id(wt.getId())
                    .amount(BigDecimal.valueOf(wt.getAmount()))
                    .type("WITHDRAWAL")
                    .status("COMPLETED")
                    .transactionDate(wt.getTransactionDate())
                    .description(wt.getDescription() != null ? wt.getDescription() : "Withdrawal from wallet")
                    .build());
        }

        // Apply filters
        if (type != null && !type.isEmpty()) {
            transactions = transactions.stream()
                    .filter(t -> t.getType().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.isEmpty()) {
            transactions = transactions.stream()
                    .filter(t -> t.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        if (startDate != null && !startDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate, formatter);
            transactions = transactions.stream()
                    .filter(t -> t.getTransactionDate() != null && t.getTransactionDate().toLocalDate().isAfter(start.minusDays(1)))
                    .collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDate end = LocalDate.parse(endDate, formatter);
            transactions = transactions.stream()
                    .filter(t -> t.getTransactionDate() != null && t.getTransactionDate().toLocalDate().isBefore(end.plusDays(1)))
                    .collect(Collectors.toList());
        }

        transactions.sort((a, b) -> {
            if (a.getTransactionDate() == null) return 1;
            if (b.getTransactionDate() == null) return -1;
            return b.getTransactionDate().compareTo(a.getTransactionDate());
        });

        Map<Long, EarningsTransactionDto> uniqueMap = new LinkedHashMap<>();
        for (EarningsTransactionDto t : transactions) {
            uniqueMap.putIfAbsent(t.getId(), t);
        }
        List<EarningsTransactionDto> uniqueTransactions = new ArrayList<>(uniqueMap.values());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), uniqueTransactions.size());

        List<EarningsTransactionDto> paginatedList = new ArrayList<>();
        if (start < uniqueTransactions.size()) {
            paginatedList = uniqueTransactions.subList(start, end);
        }

        return new PageImpl<>(paginatedList, pageable, uniqueTransactions.size());
    }

    private List<MonthlyEarningsDto> getOwnerMonthlyEarnings(Long ownerId, int year) {
        List<MonthlyEarningsDto> monthlyEarnings = new ArrayList<>();
        List<Booking> bookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.COMPLETED);

        for (int month = 1; month <= 12; month++) {
            LocalDateTime startOfMonth = LocalDate.of(year, month, 1).atStartOfDay();
            LocalDateTime endOfMonth = LocalDate.of(year, month, 1).withDayOfMonth(
                    LocalDate.of(year, month, 1).lengthOfMonth()).atTime(23, 59, 59);

            List<Booking> monthBookings = bookings.stream()
                    .filter(b -> b.getCreatedAt() != null &&
                            b.getCreatedAt().isAfter(startOfMonth) &&
                            b.getCreatedAt().isBefore(endOfMonth))
                    .collect(Collectors.toList());

            BigDecimal earnings = monthBookings.stream()
                    .map(b -> {
                        BigDecimal rental = b.getRentalAmount() != null ? b.getRentalAmount() : BigDecimal.ZERO;
                        BigDecimal insurance = b.getInsuranceFee() != null ? b.getInsuranceFee() : BigDecimal.ZERO;
                        return rental.add(insurance);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            monthlyEarnings.add(MonthlyEarningsDto.builder()
                    .month(getMonthName(month))
                    .year(year)
                    .earnings(earnings)
                    .bookingCount(monthBookings.size())
                    .build());
        }

        return monthlyEarnings;
    }

    private List<VehicleEarningsDto> getOwnerVehicleEarnings(Long ownerId) {
        List<Booking> completedBookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.COMPLETED);

        Map<Long, List<Booking>> bookingsByVehicle = completedBookings.stream()
                .filter(b -> b.getVehicle() != null)
                .collect(Collectors.groupingBy(b -> b.getVehicle().getId()));

        List<VehicleEarningsDto> vehicleEarnings = new ArrayList<>();

        for (Map.Entry<Long, List<Booking>> entry : bookingsByVehicle.entrySet()) {
            Long vehicleId = entry.getKey();
            List<Booking> vehicleBookings = entry.getValue();

            BigDecimal totalEarnings = vehicleBookings.stream()
                    .map(b -> {
                        BigDecimal rental = b.getRentalAmount() != null ? b.getRentalAmount() : BigDecimal.ZERO;
                        BigDecimal insurance = b.getInsuranceFee() != null ? b.getInsuranceFee() : BigDecimal.ZERO;
                        return rental.add(insurance);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal averagePerBooking = vehicleBookings.isEmpty() ? BigDecimal.ZERO :
                    totalEarnings.divide(BigDecimal.valueOf(vehicleBookings.size()), 2, java.math.RoundingMode.HALF_UP);

            vehicleEarnings.add(VehicleEarningsDto.builder()
                    .vehicleId(vehicleId)
                    .vehicleName(vehicleBookings.get(0).getVehicle().getBrand() + " " +
                            vehicleBookings.get(0).getVehicle().getModel())
                    .brand(vehicleBookings.get(0).getVehicle().getBrand())
                    .model(vehicleBookings.get(0).getVehicle().getModel())
                    .totalEarnings(totalEarnings)
                    .totalBookings(vehicleBookings.size())
                    .averagePerBooking(averagePerBooking)
                    .build());
        }

        vehicleEarnings.sort((a, b) -> b.getTotalEarnings().compareTo(a.getTotalEarnings()));

        return vehicleEarnings;
    }

    private Map<String, Object> getOwnerChartData(Long ownerId, String period) {
        List<Booking> bookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.COMPLETED);

        Map<String, Object> chartData = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Double> earnings = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        int monthsToShow = period.equals("3months") ? 3 : period.equals("12months") ? 12 : 6;
        LocalDate startDate = LocalDate.now().minusMonths(monthsToShow - 1);

        for (int i = 0; i < monthsToShow; i++) {
            LocalDate currentDate = startDate.plusMonths(i);
            String monthLabel = currentDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            labels.add(monthLabel);

            LocalDateTime startOfMonth = currentDate.withDayOfMonth(1).atStartOfDay();
            LocalDateTime endOfMonth = currentDate.withDayOfMonth(currentDate.lengthOfMonth()).atTime(23, 59, 59);

            List<Booking> monthBookings = bookings.stream()
                    .filter(b -> b.getCreatedAt() != null &&
                            b.getCreatedAt().isAfter(startOfMonth) &&
                            b.getCreatedAt().isBefore(endOfMonth))
                    .collect(Collectors.toList());

            double monthEarnings = monthBookings.stream()
                    .mapToDouble(b -> {
                        double rental = b.getRentalAmount() != null ? b.getRentalAmount().doubleValue() : 0.0;
                        double insurance = b.getInsuranceFee() != null ? b.getInsuranceFee().doubleValue() : 0.0;
                        return rental + insurance;
                    })
                    .sum();

            earnings.add(monthEarnings);
            counts.add(monthBookings.size());
        }

        chartData.put("labels", labels);
        chartData.put("earnings", earnings);
        chartData.put("counts", counts);

        return chartData;
    }

    private ResponseEntity<byte[]> exportOwnerEarnings(Long ownerId, String startDate, String endDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Transaction Type,Amount,Status,Description\n");

        List<Booking> bookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.COMPLETED);

        for (Booking booking : bookings) {
            Double totalAmount = 0.0;
            if (booking.getRentalAmount() != null) totalAmount += booking.getRentalAmount().doubleValue();
            if (booking.getInsuranceFee() != null) totalAmount += booking.getInsuranceFee().doubleValue();

            if (totalAmount > 0) {
                csv.append(booking.getCreatedAt() != null ?
                                booking.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                        .append(",RENTAL + INSURANCE,")
                        .append(totalAmount)
                        .append(",COMPLETED,")
                        .append("Rental + Insurance payment for booking: ").append(booking.getBookingReference())
                        .append("\n");
            }
        }

        byte[] csvBytes = csv.toString().getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "text/csv");
        headers.add("Content-Disposition", "attachment; filename=earnings_export.csv");

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    private String getMonthName(int month) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        return months[month - 1];
    }

    // ============ DEBUG METHOD ============

    public Map<String, Object> debugAdminEarnings() {
        Map<String, Object> debug = new HashMap<>();

        List<WalletTransaction> allTransactions = walletTransactionRepository.findAll();

        List<WalletTransaction> commissionTransactions = allTransactions.stream()
                .filter(wt -> wt.getType() == WalletTransaction.TransactionType.CREDIT)
                .filter(wt -> wt.getDescription() != null &&
                        wt.getDescription().toLowerCase().contains("service fee"))
                .collect(Collectors.toList());

        debug.put("totalWalletTransactions", allTransactions.size());
        debug.put("commissionTransactions", commissionTransactions.size());

        List<Map<String, Object>> transactionDetails = new ArrayList<>();
        double totalCommission = 0;

        for (WalletTransaction wt : commissionTransactions) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", wt.getId());
            detail.put("amount", wt.getAmount());
            detail.put("description", wt.getDescription());
            detail.put("transactionDate", wt.getTransactionDate());
            detail.put("referenceId", wt.getReferenceId());
            detail.put("userId", wt.getUser() != null ? wt.getUser().getId() : null);

            transactionDetails.add(detail);
            totalCommission += wt.getAmount();
        }

        debug.put("transactions", transactionDetails);
        debug.put("totalCommission", totalCommission);

        long totalBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.COMPLETED);
        debug.put("totalCompletedBookings", totalBookings);

        EarningsSummaryDto summary = getAdminCommissionSummary();
        debug.put("adminSummaryTotalEarnings", summary.getTotalEarnings());
        debug.put("adminSummaryTotalBookings", summary.getTotalBookings());
        debug.put("adminSummaryCurrentMonthEarnings", summary.getCurrentMonthEarnings());
        debug.put("adminSummaryAvailableBalance", summary.getAvailableBalance());

        return debug;
    }
}