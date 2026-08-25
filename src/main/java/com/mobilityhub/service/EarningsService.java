// com/mobilityhub/service/EarningsService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.response.EarningsSummaryDto;
import com.mobilityhub.dto.response.EarningsTransactionDto;
import com.mobilityhub.dto.response.MonthlyEarningsDto;
import com.mobilityhub.dto.response.VehicleEarningsDto;
import com.mobilityhub.model.Booking;
import com.mobilityhub.model.User;
import com.mobilityhub.model.WalletTransaction;
import com.mobilityhub.repository.BookingRepository;
import com.mobilityhub.repository.UserRepository;
import com.mobilityhub.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    public EarningsSummaryDto getEarningsSummary(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Booking> completedBookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.COMPLETED);
        List<Booking> confirmedBookings = bookingRepository.findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.CONFIRMED);

        // ✅ CORRECT: Rental + Insurance goes to Owner
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

    public Page<EarningsTransactionDto> getEarningsTransactions(Long ownerId, String type, String status,
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

    public List<MonthlyEarningsDto> getMonthlyEarnings(Long ownerId, int year) {
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

    public List<VehicleEarningsDto> getVehicleEarnings(Long ownerId) {
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

    public Map<String, Object> getChartData(Long ownerId, String period) {
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

    public ResponseEntity<byte[]> exportEarnings(Long ownerId, String startDate, String endDate) {
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
}