// com/mobilityhub/repository/WalletTransactionRepository.java
package com.mobilityhub.repository;

import com.mobilityhub.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUserIdOrderByTransactionDateDesc(Long userId);
    List<WalletTransaction> findByUserIdAndType(Long userId, WalletTransaction.TransactionType type);
}