package com.shopaccgame.repository;

import com.shopaccgame.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Integer userId);
    List<Transaction> findAllByOrderByCreatedAtDesc();
    Optional<Transaction> findFirstByReferenceIdAndType(Integer referenceId, String type);
}
