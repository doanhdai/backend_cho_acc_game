package com.shopaccgame.controller;

import com.shopaccgame.model.*;
import com.shopaccgame.repository.*;
import com.shopaccgame.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final TransactionRepository transactionRepository;

    @Data
    public static class CreateOrderRequest {
        private Integer account_id;
    }

    @Data
    public static class SendMessageRequest {
        private String message;
        private Boolean is_private;
    }

    @Data
    public static class CancelOrderRequest {
        private String reason;
    }

    @PostMapping("/middleman/create")
    @Transactional
    public ResponseEntity<Map<String, Object>> createMiddlemanOrder(@RequestBody CreateOrderRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (req.getAccount_id() == null) {
            response.put("success", false);
            response.put("message", "Thiếu ID tài khoản cần mua");
            return ResponseEntity.badRequest().body(response);
        }

        // 1. Fetch account with WRITE LOCK
        Account account = accountRepository.findByIdWithWriteLock(req.getAccount_id())
                .orElse(null);
        if (account == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy tài khoản game");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        if (!"SHOWING".equals(account.getStatus())) {
            response.put("success", false);
            response.put("message", "Tài khoản này hiện không sẵn sàng giao dịch (Đã bán hoặc đang có giao dịch khác)");
            return ResponseEntity.badRequest().body(response);
        }

        if (account.getSeller().getId().equals(userPrincipal.getId())) {
            response.put("success", false);
            response.put("message", "Bạn không thể tự mua tài khoản của chính mình");
            return ResponseEntity.badRequest().body(response);
        }

        BigDecimal price = account.getPrice();

        // 2. Fetch buyer and check balance
        User buyer = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Người mua không tồn tại"));

        if (buyer.getBalance().compareTo(price) < 0) {
            BigDecimal diff = price.subtract(buyer.getBalance());
            response.put("success", false);
            response.put("message", "Số dư ví không đủ. Cần thêm " + diff + "đ để thực hiện giao dịch.");
            return ResponseEntity.badRequest().body(response);
        }

        // 3. Lock buyer balance into frozen balance
        BigDecimal buyerBalanceBefore = buyer.getBalance();
        buyer.setBalance(buyerBalanceBefore.subtract(price));
        buyer.setFrozenBalance(buyer.getFrozenBalance().add(price));
        userRepository.save(buyer);

        // 4. Update account status
        account.setStatus("IN_TRANSACTION");
        accountRepository.save(account);

        // 5. Create Order
        BigDecimal fee = price.multiply(new BigDecimal("0.03")); // 3% fee
        Order order = Order.builder()
                .buyer(buyer)
                .seller(account.getSeller())
                .account(account)
                .amount(price)
                .fee(fee)
                .status("PENDING")
                .build();
        order = orderRepository.save(order);

        // 6. Write transaction log
        Transaction tx = Transaction.builder()
                .user(buyer)
                .amount(price)
                .type("MIDDLEMAN_HOLD")
                .balanceBefore(buyerBalanceBefore)
                .balanceAfter(buyer.getBalance())
                .description("Đóng băng tiền mua acc qua trung gian: " + account.getTitle())
                .referenceId(order.getId())
                .build();
        transactionRepository.save(tx);

        // 7. Write system welcome message
        String welcomeMsg = "HỆ THỐNG: Đơn hàng #" + order.getId() + " đã được khởi tạo. Số tiền " + String.format("%,.0f", price) + "đ đã được khóa an toàn. Admin trung gian sẽ sớm tham gia phòng chat.";
        Message msg = Message.builder()
                .order(order)
                .sender(buyer) // Sent on behalf of buyer but marked as HỆ THỐNG text
                .message(welcomeMsg)
                .isPrivate(false)
                .build();
        messageRepository.save(msg);

        response.put("success", true);
        response.put("message", "Khởi tạo đơn hàng trung gian thành công!");
        response.put("orderId", order.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/middleman/{id}")
    public ResponseEntity<Map<String, Object>> getOrderDetails(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy đơn hàng");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        boolean isBuyer = userPrincipal.getId().equals(order.getBuyer().getId());
        boolean isSeller = userPrincipal.getId().equals(order.getSeller().getId());
        boolean isAdmin = "admin".equalsIgnoreCase(userPrincipal.getRole());

        if (!isBuyer && !isSeller && !isAdmin) {
            response.put("success", false);
            response.put("message", "Bạn không có quyền truy cập vào phòng chat đơn hàng này");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", order.getId());
        data.put("buyer_id", order.getBuyer().getId());
        data.put("seller_id", order.getSeller().getId());
        data.put("account_id", order.getAccount().getId());
        data.put("amount", order.getAmount());
        data.put("fee", order.getFee());
        data.put("status", order.getStatus());
        data.put("cancel_reason", order.getCancelReason());
        data.put("created_at", order.getCreatedAt());
        data.put("updated_at", order.getUpdatedAt());

        data.put("account_title", order.getAccount().getTitle());
        data.put("account_price", order.getAccount().getPrice());
        data.put("server", order.getAccount().getServer());
        data.put("level", order.getAccount().getLevel());
        data.put("rank_level", order.getAccount().getRankLevel());
        data.put("images", order.getAccount().getImages());

        data.put("buyer_name", order.getBuyer().getUsername());
        data.put("buyer_phone", order.getBuyer().getPhoneZalo());

        data.put("seller_name", order.getSeller().getUsername());
        data.put("seller_phone", order.getSeller().getPhoneZalo());

        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/middleman/{id}/messages")
    public ResponseEntity<Map<String, Object>> getOrderMessages(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy đơn hàng");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        boolean isBuyer = userPrincipal.getId().equals(order.getBuyer().getId());
        boolean isSeller = userPrincipal.getId().equals(order.getSeller().getId());
        boolean isAdmin = "admin".equalsIgnoreCase(userPrincipal.getRole());

        if (!isBuyer && !isSeller && !isAdmin) {
            response.put("success", false);
            response.put("message", "Không có quyền truy cập");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        List<Message> messages = messageRepository.findByOrderIdOrderByCreatedAtAsc(id);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Message m : messages) {
            // Censor private admin messages for normal users
            if (m.getIsPrivate() && !isAdmin) {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("order_id", m.getOrder().getId());
            map.put("sender_id", m.getSender().getId());
            map.put("message", m.getMessage());
            map.put("is_private", m.getIsPrivate() ? 1 : 0);
            map.put("created_at", m.getCreatedAt());
            map.put("sender_name", m.getSender().getUsername());
            map.put("sender_role", m.getSender().getRole());
            list.add(map);
        }

        response.put("success", true);
        response.put("data", list);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/middleman/{id}/messages")
    @Transactional
    public ResponseEntity<Map<String, Object>> sendOrderMessage(@PathVariable("id") Integer id, @RequestBody SendMessageRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (req.getMessage() == null || req.getMessage().trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Nội dung tin nhắn không được trống");
            return ResponseEntity.badRequest().body(response);
        }

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            response.put("success", false);
            response.put("message", "Đơn hàng không tồn tại");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        boolean isBuyer = userPrincipal.getId().equals(order.getBuyer().getId());
        boolean isSeller = userPrincipal.getId().equals(order.getSeller().getId());
        boolean isAdmin = "admin".equalsIgnoreCase(userPrincipal.getRole());

        if (!isBuyer && !isSeller && !isAdmin) {
            response.put("success", false);
            response.put("message", "Không có quyền gửi tin nhắn");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        User sender = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        Message msg = Message.builder()
                .order(order)
                .sender(sender)
                .message(req.getMessage())
                .isPrivate(isAdmin && req.getIs_private() != null && req.getIs_private())
                .build();
        messageRepository.save(msg);

        response.put("success", true);
        response.put("message", "Đã gửi tin nhắn");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/middleman/{id}/complete")
    @Transactional
    public ResponseEntity<Map<String, Object>> completeMiddlemanOrder(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal) || !"admin".equalsIgnoreCase(userPrincipal.getRole())) {
            response.put("success", false);
            response.put("message", "Chỉ Admin mới có quyền xác nhận hoàn tất đơn hàng");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy đơn hàng");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        if (!"PENDING".equals(order.getStatus())) {
            response.put("success", false);
            response.put("message", "Đơn hàng đã được xử lý hoàn tất hoặc hủy trước đó");
            return ResponseEntity.badRequest().body(response);
        }

        BigDecimal price = order.getAmount();
        BigDecimal fee = order.getFee();
        BigDecimal sellerReceives = price.subtract(fee);

        User buyer = userRepository.findById(order.getBuyer().getId())
                .orElseThrow(() -> new RuntimeException("Buyer not found"));
        User seller = userRepository.findById(order.getSeller().getId())
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        if (buyer.getFrozenBalance().compareTo(price) < 0) {
            response.put("success", false);
            response.put("message", "Lỗi đồng bộ: Số dư đóng băng của người mua thấp hơn giá trị đơn hàng");
            return ResponseEntity.badRequest().body(response);
        }

        // 1. Deduct frozen balance from buyer
        buyer.setFrozenBalance(buyer.getFrozenBalance().subtract(price));
        userRepository.save(buyer);

        // 2. Transfer proceeds to seller balance
        BigDecimal sellerBalanceBefore = seller.getBalance();
        seller.setBalance(sellerBalanceBefore.add(sellerReceives));
        userRepository.save(seller);

        // 3. Update order and account statuses
        order.setStatus("COMPLETED");
        orderRepository.save(order);

        Account account = order.getAccount();
        account.setStatus("SOLD");
        accountRepository.save(account);

        // 4. Log wallet transaction for seller
        Transaction tx = Transaction.builder()
                .user(seller)
                .amount(sellerReceives)
                .type("MIDDLEMAN_RELEASE")
                .balanceBefore(sellerBalanceBefore)
                .balanceAfter(seller.getBalance())
                .description("Nhận tiền bán acc trung gian: Đơn #" + order.getId() + " (Đã khấu trừ 3% phí sàn)")
                .referenceId(order.getId())
                .build();
        transactionRepository.save(tx);

        // 5. System chat message release log
        User admin = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        String completeMsg = "HỆ THỐNG: Admin đã xác nhận hoàn tất đơn hàng. Số tiền " + String.format("%,.0f", sellerReceives) + "đ đã được chuyển vào ví Người bán. Giao dịch thành công và phòng chat kết thúc.";
        Message sysMsg = Message.builder()
                .order(order)
                .sender(admin)
                .message(completeMsg)
                .isPrivate(false)
                .build();
        messageRepository.save(sysMsg);

        response.put("success", true);
        response.put("message", "Xác nhận hoàn tất đơn hàng thành công, tiền đã giải ngân cho người bán!");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/middleman/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelMiddlemanOrder(@PathVariable("id") Integer id, @RequestBody CancelOrderRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal) || !"admin".equalsIgnoreCase(userPrincipal.getRole())) {
            response.put("success", false);
            response.put("message", "Chỉ Admin mới có quyền hủy đơn hàng trung gian");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        if (req.getReason() == null || req.getReason().trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Vui lòng cung cấp lý do hủy đơn hàng");
            return ResponseEntity.badRequest().body(response);
        }

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy đơn hàng");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        if (!"PENDING".equals(order.getStatus())) {
            response.put("success", false);
            response.put("message", "Đơn hàng đã được xử lý hoàn tất hoặc hủy trước đó");
            return ResponseEntity.badRequest().body(response);
        }

        BigDecimal price = order.getAmount();

        User buyer = userRepository.findById(order.getBuyer().getId())
                .orElseThrow(() -> new RuntimeException("Buyer not found"));

        if (buyer.getFrozenBalance().compareTo(price) < 0) {
            response.put("success", false);
            response.put("message", "Số dư đóng băng người mua không đủ");
            return ResponseEntity.badRequest().body(response);
        }

        // 1. Unfreeze funds back to buyer main balance
        BigDecimal buyerBalanceBefore = buyer.getBalance();
        buyer.setFrozenBalance(buyer.getFrozenBalance().subtract(price));
        buyer.setBalance(buyerBalanceBefore.add(price));
        userRepository.save(buyer);

        // 2. Set order status & cancel reason
        order.setStatus("CANCELLED");
        order.setCancelReason(req.getReason());
        orderRepository.save(order);

        // 3. Unlist account (status HIDDEN)
        Account account = order.getAccount();
        account.setStatus("HIDDEN");
        accountRepository.save(account);

        // 4. Log refund transaction
        Transaction tx = Transaction.builder()
                .user(buyer)
                .amount(price)
                .type("REFUND")
                .balanceBefore(buyerBalanceBefore)
                .balanceAfter(buyer.getBalance())
                .description("Hoàn tiền hủy đơn trung gian #" + order.getId() + ". Lý do: " + req.getReason())
                .referenceId(order.getId())
                .build();
        transactionRepository.save(tx);

        // 5. System chat message log
        User admin = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        String cancelMsg = "HỆ THỐNG: Đơn hàng đã bị hủy bởi Admin. Lý do: " + req.getReason() + ". Số tiền " + String.format("%,.0f", price) + "đ đã được hoàn trả về số dư ví người mua.";
        Message sysMsg = Message.builder()
                .order(order)
                .sender(admin)
                .message(cancelMsg)
                .isPrivate(false)
                .build();
        messageRepository.save(sysMsg);

        response.put("success", true);
        response.put("message", "Đã hủy đơn và hoàn trả tiền cho người mua thành công!");
        return ResponseEntity.ok(response);
    }
}
