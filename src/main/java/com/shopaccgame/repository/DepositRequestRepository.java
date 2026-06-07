package com.shopaccgame.repository;

import com.shopaccgame.model.DepositRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DepositRequestRepository extends JpaRepository<DepositRequest, Integer> {
    List<DepositRequest> findByUserIdOrderByCreatedAtDesc(Integer userId);
    List<DepositRequest> findAllByOrderByCreatedAtDesc();
}
