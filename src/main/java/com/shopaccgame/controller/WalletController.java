package com.shopaccgame.controller;

import com.shopaccgame.model.*;
import com.shopaccgame.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final UserRepository userRepository;
    private final DepositRequestRepository depositRequestRepository;
    private final TransactionRepository transactionRepository;

    @Data
    public static class CallbackRequest {
        private String description;
        private BigDecimal amount;
    }

    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Map<String, Object>> walletCallback(@RequestBody CallbackRequest req) {
        Map<String, Object> response = new HashMap<>();

        if (req.getDescription() == null || req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            response.put("success", false);
            response.put("message", "Dữ liệu không hợp lệ. Cần truyền description và amount.");
            return ResponseEntity.badRequest().body(response);
        }

        // Parse description for user ID e.g. NAP_USER123
        Pattern pattern = Pattern.compile("NAP_USER(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(req.getDescription());

        if (!matcher.find()) {
            response.put("success", false);
            response.put("message", "Nội dung chuyển khoản không đúng cú pháp nạp tiền (NAP_USER[ID])");
            return ResponseEntity.badRequest().body(response);
        }

        int userId;
        try {
            userId = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            response.put("success", false);
            response.put("message", "ID người dùng không hợp lệ");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy người dùng có ID này");
            return ResponseEntity.status(404).body(response);
        }

        BigDecimal depositAmount = req.getAmount();
        BigDecimal balanceBefore = user.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(depositAmount);

        // 1. Credit user balance
        user.setBalance(balanceAfter);
        userRepository.save(user);

        // 2. Create approved deposit request record
        DepositRequest deposit = DepositRequest.builder()
                .user(user)
                .amount(depositAmount)
                .method("bank_transfer")
                .transactionRef("AUTO" + System.currentTimeMillis())
                .note(req.getDescription())
                .status("approved")
                .adminNote("VietQR Auto Webhook")
                .processedAt(LocalDateTime.now())
                .build();
        deposit = depositRequestRepository.save(deposit);

        // 3. Write transaction log
        Transaction tx = Transaction.builder()
                .user(user)
                .amount(depositAmount)
                .type("DEPOSIT")
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description("Nạp tiền tự động qua VietQR: " + req.getDescription())
                .referenceId(deposit.getId())
                .build();
        transactionRepository.save(tx);

        response.put("success", true);
        response.put("message", "Nạp tiền tự động thành công cho User #" + userId + ". Đã cộng " + depositAmount + "đ vào ví.");
        return ResponseEntity.ok(response);
    }
}
