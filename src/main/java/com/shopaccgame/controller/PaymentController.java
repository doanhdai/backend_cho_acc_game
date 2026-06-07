package com.shopaccgame.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopaccgame.model.DepositRequest;
import com.shopaccgame.model.User;
import com.shopaccgame.repository.DepositRequestRepository;
import com.shopaccgame.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.payos.PayOS;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final DepositRequestRepository depositRequestRepository;
    private final UserRepository userRepository;
    private final PayOS payOS;
    private final ObjectMapper objectMapper;

    @PostMapping("/payos-webhook")
    @Transactional
    public ResponseEntity<?> handlePayOSWebhook(@RequestBody Map<String, Object> body) {
        try {
            // 1. Convert request body map to PayOS Webhook type using Jackson ObjectMapper
            JsonNode jsonNode = objectMapper.valueToTree(body);
            Webhook webhookBody = objectMapper.treeToValue(jsonNode, Webhook.class);

            // 2. Verify signature to ensure the webhook request comes from PayOS
            WebhookData data = payOS.verifyPaymentWebhookData(webhookBody);

            // 3. Extract deposit record code (mapped to DepositRequest ID)
            int depositId = data.getOrderCode().intValue();

            // 4. Find the matching deposit request in database
            DepositRequest deposit = depositRequestRepository.findById(depositId).orElse(null);
            if (deposit == null) {
                return ResponseEntity.status(404).body("Deposit request not found");
            }

            // 5. If the request status is pending, mark it completed and credit user balance
            if ("pending".equalsIgnoreCase(deposit.getStatus())) {
                deposit.setStatus("completed");
                deposit.setTransactionRef(data.getPaymentLinkId());
                deposit.setAdminNote("Duyệt tự động qua PayOS (VietQR)");
                depositRequestRepository.save(deposit);

                User user = deposit.getUser();
                user.setBalance(user.getBalance().add(deposit.getAmount()));
                userRepository.save(user);
            }

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Signature verification failed: " + e.getMessage());
        }
    }
}
