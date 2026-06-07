package com.shopaccgame.controller;

import com.shopaccgame.model.*;
import com.shopaccgame.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.shopaccgame.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.payos.PayOS;
import vn.payos.type.PaymentData;
import vn.payos.type.CheckoutResponseData;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final DepositRequestRepository depositRequestRepository;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PayOS payOS;

    @Data
    public static class CreateDepositRequest {
        private BigDecimal amount;
        private String method;
        private String transaction_ref;
        private String note;
    }

    @Data
    public static class UpdateProfileRequest {
        private String full_name;
        private String email;
        private String phone_zalo;
        private String avatar;
    }

    @PostMapping("/deposit")
    @Transactional
    public ResponseEntity<Map<String, Object>> createDeposit(@RequestBody CreateDepositRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (req.getAmount() == null || req.getAmount().compareTo(new BigDecimal("10000")) < 0) {
            response.put("success", false);
            response.put("message", "Số tiền nạp tối thiểu là 10,000đ");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

        DepositRequest deposit = DepositRequest.builder()
                .user(user)
                .amount(req.getAmount())
                .method(req.getMethod() != null ? req.getMethod() : "bank_transfer")
                .transactionRef(req.getTransaction_ref())
                .note(req.getNote())
                .status("pending")
                .build();

        deposit = depositRequestRepository.save(deposit);

        if ("vietqr".equalsIgnoreCase(req.getMethod())) {
            try {
                long orderCode = deposit.getId();
                int amountVal = req.getAmount().intValue();
                // Description can only contain alphanumeric and space, max 25 chars.
                String description = "Nap " + amountVal + "d u" + user.getId();
                if (description.length() > 25) {
                    description = description.substring(0, 25);
                }

                String returnUrl = System.getProperty("PAYOS_RETURN_URL");
                if (returnUrl == null || returnUrl.isEmpty()) {
                    returnUrl = "http://localhost:5173/profile?tab=wallet&deposit_status=success";
                }
                String cancelUrl = System.getProperty("PAYOS_CANCEL_URL");
                if (cancelUrl == null || cancelUrl.isEmpty()) {
                    cancelUrl = "http://localhost:5173/profile?tab=wallet&deposit_status=cancelled";
                }

                PaymentData paymentData = PaymentData.builder()
                        .orderCode(orderCode)
                        .amount(amountVal)
                        .description(description)
                        .returnUrl(returnUrl)
                        .cancelUrl(cancelUrl)
                        .build();

                CheckoutResponseData payOSRes = payOS.createPaymentLink(paymentData);

                response.put("success", true);
                response.put("checkoutUrl", payOSRes.getCheckoutUrl());
                response.put("id", deposit.getId());
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                depositRequestRepository.delete(deposit);
                response.put("success", false);
                response.put("message", "Lỗi tạo link thanh toán PayOS: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        }

        response.put("success", true);
        response.put("message", "Yêu cầu nạp tiền đã được gửi. Vui lòng chờ admin xét duyệt.");
        response.put("id", deposit.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/deposits")
    public ResponseEntity<Map<String, Object>> getMyDeposits() {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        List<DepositRequest> deposits = depositRequestRepository.findByUserIdOrderByCreatedAtDesc(userPrincipal.getId());
        
        List<Map<String, Object>> data = new ArrayList<>();
        for (DepositRequest d : deposits) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("user_id", d.getUser().getId());
            map.put("amount", d.getAmount());
            map.put("method", d.getMethod());
            map.put("transaction_ref", d.getTransactionRef());
            map.put("note", d.getNote());
            map.put("status", d.getStatus());
            map.put("admin_note", d.getAdminNote());
            map.put("created_at", d.getCreatedAt());
            map.put("updated_at", d.getUpdatedAt());
            data.add(map);
        }

        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getMyHistory(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // Just fetch all and paginate in memory, or use Spring Pageable.
        // In memory pagination is extremely quick for standard lists.
        List<Transaction> allTxs = transactionRepository.findByUserIdOrderByCreatedAtDesc(userPrincipal.getId());
        int total = allTxs.size();
        
        int fromIndex = (page - 1) * limit;
        int toIndex = Math.min(fromIndex + limit, total);
        
        List<Transaction> pagedTxs = new ArrayList<>();
        if (fromIndex < total) {
            pagedTxs = allTxs.subList(fromIndex, toIndex);
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Transaction t : pagedTxs) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", t.getId());
            map.put("user_id", t.getUser().getId());
            map.put("amount", t.getAmount());
            map.put("type", t.getType());
            map.put("balance_before", t.getBalanceBefore());
            map.put("balance_after", t.getBalanceAfter());
            map.put("description", t.getDescription());
            map.put("reference_id", t.getReferenceId());
            map.put("created_at", t.getCreatedAt());
            data.add(map);
        }

        response.put("success", true);
        response.put("data", data);
        response.put("total", total);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-accounts")
    public ResponseEntity<Map<String, Object>> getMyAccounts() {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Integer userId = userPrincipal.getId();

        // 1. Get escrow orders (buyer/seller)
        List<Order> orders = orderRepository.findMyOrders(userId);
        List<Map<String, Object>> ordersList = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", o.getId());
            map.put("buyer_id", o.getBuyer().getId());
            map.put("seller_id", o.getSeller().getId());
            map.put("account_id", o.getAccount().getId());
            map.put("amount", o.getAmount());
            map.put("fee", o.getFee());
            map.put("status", o.getStatus());
            map.put("cancel_reason", o.getCancelReason());
            map.put("created_at", o.getCreatedAt());

            map.put("title", o.getAccount().getTitle());
            map.put("username", o.getAccount().getUsername());
            map.put("password", o.getAccount().getPassword());
            map.put("email_acc", o.getAccount().getEmailAcc());
            map.put("email_pass", o.getAccount().getEmailPass());
            map.put("server", o.getAccount().getServer());
            map.put("level", o.getAccount().getLevel());
            map.put("rank_level", o.getAccount().getRankLevel());
            map.put("images", o.getAccount().getImages());
            map.put("category_name", o.getAccount().getCategory().getName());
            map.put("buyer_name", o.getBuyer().getUsername());
            map.put("seller_name", o.getSeller().getUsername());
            ordersList.add(map);
        }

        // 2. Get active listings uploaded by user
        List<Account> listings = accountRepository.findBySellerId(userId);
        List<Map<String, Object>> listingsList = new ArrayList<>();
        for (Account a : listings) {
            if ("DELETED".equalsIgnoreCase(a.getStatus())) {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("title", a.getTitle());
            map.put("description", a.getDescription());
            map.put("price", a.getPrice());
            map.put("original_price", a.getOriginalPrice());
            map.put("server", a.getServer());
            map.put("level", a.getLevel());
            map.put("rank_level", a.getRankLevel());
            map.put("champions_count", a.getChampionsCount());
            map.put("skins_count", a.getSkinsCount());
            map.put("security_status", a.getSecurityStatus());
            map.put("status", a.getStatus());
            map.put("images", a.getImages());
            map.put("is_featured", a.getIsFeatured());
            map.put("view_count", a.getViewCount());
            map.put("created_at", a.getCreatedAt());
            map.put("category_name", a.getCategory().getName());
            listingsList.add(map);
        }

        response.put("success", true);
        response.put("orders", ordersList);
        response.put("listings", listingsList);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody UpdateProfileRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

        if (req.getEmail() != null && !req.getEmail().trim().isEmpty() && !req.getEmail().equalsIgnoreCase(user.getEmail())) {
            Optional<User> existingEmail = userRepository.findByEmail(req.getEmail());
            if (existingEmail.isPresent()) {
                response.put("success", false);
                response.put("message", "Email đã được sử dụng bởi tài khoản khác");
                return ResponseEntity.badRequest().body(response);
            }
            user.setEmail(req.getEmail());
        }

        if (req.getFull_name() != null) user.setFullName(req.getFull_name());
        if (req.getPhone_zalo() != null && !req.getPhone_zalo().trim().isEmpty()) {
            if (!req.getPhone_zalo().trim().matches("^0[0-9]{9}$")) {
                response.put("success", false);
                response.put("message", "Số điện thoại Zalo không đúng định dạng (phải có 10 chữ số và bắt đầu bằng số 0)");
                return ResponseEntity.badRequest().body(response);
            }
            user.setPhoneZalo(req.getPhone_zalo().trim());
        } else if (req.getPhone_zalo() != null) {
            user.setPhoneZalo(null);
        }
        if (req.getAvatar() != null) user.setAvatar(req.getAvatar());

        user = userRepository.save(user);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("email", user.getEmail());
        userMap.put("full_name", user.getFullName());
        userMap.put("role", user.getRole());
        userMap.put("balance", user.getBalance());
        userMap.put("frozen_balance", user.getFrozenBalance());
        userMap.put("avatar", user.getAvatar());
        userMap.put("phone_zalo", user.getPhoneZalo());

        response.put("success", true);
        response.put("message", "Cập nhật thông tin thành công");
        response.put("user", userMap);

        return ResponseEntity.ok(response);
    }

    @Data
    public static class ChangePasswordRequest {
        private String old_password;
        private String new_password;
    }

    @PutMapping("/profile/change-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

        if (!passwordEncoder.matches(req.getOld_password(), user.getPassword())) {
            response.put("success", false);
            response.put("message", "Mật khẩu cũ không chính xác");
            return ResponseEntity.badRequest().body(response);
        }

        if (req.getNew_password() == null || req.getNew_password().trim().length() < 6) {
            response.put("success", false);
            response.put("message", "Mật khẩu mới phải có ít nhất 6 ký tự");
            return ResponseEntity.badRequest().body(response);
        }

        user.setPassword(passwordEncoder.encode(req.getNew_password()));
        userRepository.save(user);

        response.put("success", true);
        response.put("message", "Thay đổi mật khẩu thành công!");
        return ResponseEntity.ok(response);
    }
}
