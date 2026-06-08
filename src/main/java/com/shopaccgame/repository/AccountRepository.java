package com.shopaccgame.repository;

import com.shopaccgame.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer>, AccountRepositoryCustom {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithWriteLock(@Param("id") Integer id);
    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.buyer.id = :buyerId AND o.account.id = :accountId AND o.status = 'COMPLETED'")
    boolean checkBuyer(@Param("buyerId") Integer buyerId, @Param("accountId") Integer accountId);

    List<Account> findBySellerId(Integer sellerId);

    @Query(value = "SELECT COUNT(*) FROM accounts WHERE seller_id = :sellerId AND created_at >= NOW() - INTERVAL 5 MINUTE", nativeQuery = true)
    long countRecentPosts(@Param("sellerId") Integer sellerId);
}
