// com/mobilityhub/service/WalletService.java
package com.mobilityhub.service;

import com.mobilityhub.model.User;
import com.mobilityhub.model.WalletTransaction;
import com.mobilityhub.repository.UserRepository;
import com.mobilityhub.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public void addBalance(Long userId, Double amount, String description, String referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update user balance
        user.setBalance(user.getBalance() + amount);
        userRepository.save(user);

        // Create transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .user(user)
                .amount(amount)
                .type(WalletTransaction.TransactionType.CREDIT)
                .description(description)
                .transactionDate(LocalDateTime.now())
                .referenceId(referenceId)
                .build();

        walletTransactionRepository.save(transaction);

        log.info("💰 Added ₹{} to user {} balance. New balance: ₹{}",
                amount, user.getEmail(), user.getBalance());
    }

    @Transactional
    public void deductBalance(Long userId, Double amount, String description, String referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        user.setBalance(user.getBalance() - amount);
        userRepository.save(user);

        WalletTransaction transaction = WalletTransaction.builder()
                .user(user)
                .amount(amount)
                .type(WalletTransaction.TransactionType.DEBIT)
                .description(description)
                .transactionDate(LocalDateTime.now())
                .referenceId(referenceId)
                .build();

        walletTransactionRepository.save(transaction);

        log.info("💰 Deducted ₹{} from user {} balance. New balance: ₹{}",
                amount, user.getEmail(), user.getBalance());
    }

    public Double getBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getBalance();
    }
}