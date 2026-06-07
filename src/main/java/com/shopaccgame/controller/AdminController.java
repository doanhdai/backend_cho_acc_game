package com.shopaccgame.controller;

import com.shopaccgame.model.*;
import com.shopaccgame.repository.*;
import com.shopaccgame.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import com.shopaccgame.service.TelegramService;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AccountRepository accountRepository;
    private final TelegramService telegramService;
    private final CategoryRepository categoryRepository;
    private final SkinRepository skinRepository;
    private final OrderRepository orderRepository;
    private final DepositRequestRepository depositRequestRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Data
    public static class AdminCreateAccountRequest {
        private Integer category_id;
        private String title;
        private String description;
        private BigDecimal price;
        private BigDecimal original_price;
        private String username;
        private String password;
        private String email_acc;
        private String email_pass;
        private String server;
        private Integer level;
        private String rank_level;
        private Integer champions_count;
        private Integer skins_count;
        private String security_status;
        private List<String> images;
        private Boolean is_featured;
    }

    @Data
    public static class SkinRequest {
        private String champion_name;
        private String skin_name;
        private String image_url;
    }

    @Data
    public static class NoteRequest {
        private String admin_note;
    }

    // Accounts
    @GetMapping("/accounts")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> adminGetAccounts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) Integer categoryId,
            @RequestParam(value = "search", required = false) String search
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            StringBuilder sql = new StringBuilder("SELECT a.* FROM accounts a JOIN categories c ON a.category_id = c.id JOIN users u ON a.seller_id = u.id ");
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM accounts a JOIN categories c ON a.category_id = c.id JOIN users u ON a.seller_id = u.id ");
            
            List<String> wheres = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            
            if (status != null && !status.trim().isEmpty()) {
                wheres.add("a.status = :status");
                params.put("status", status);
            }
            if (categoryId != null) {
                wheres.add("a.category_id = :categoryId");
                params.put("categoryId", categoryId);
            }
            if (search != null && !search.trim().isEmpty()) {
                wheres.add("(a.title LIKE :search OR a.description LIKE :search OR a.username LIKE :search OR u.username LIKE :search)");
                params.put("search", "%" + search + "%");
            }
            
            if (!wheres.isEmpty()) {
                String wheresStr = " WHERE " + String.join(" AND ", wheres);
                sql.append(wheresStr);
                countSql.append(wheresStr);
            }
            
            sql.append(" ORDER BY a.created_at DESC");
            
            int offset = (page - 1) * limit;
            var query = entityManager.createNativeQuery(sql.toString(), Account.class);
            var countQuery = entityManager.createNativeQuery(countSql.toString());
            
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            
            List<Account> accounts = query.getResultList();
            long total = ((Number) countQuery.getSingleResult()).longValue();
            
            List<Map<String, Object>> data = new ArrayList<>();
            for (Account acc : accounts) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", acc.getId());
                m.put("title", acc.getTitle());
                m.put("description", acc.getDescription());
                m.put("price", acc.getPrice());
                m.put("original_price", acc.getOriginalPrice());
                m.put("username", acc.getUsername());
                m.put("password", acc.getPassword());
                m.put("email_acc", acc.getEmailAcc());
                m.put("email_pass", acc.getEmailPass());
                m.put("server", acc.getServer());
                m.put("level", acc.getLevel());
                m.put("rank_level", acc.getRankLevel());
                m.put("champions_count", acc.getChampionsCount());
                m.put("skins_count", acc.getSkinsCount());
                m.put("security_status", acc.getSecurityStatus());
                m.put("status", acc.getStatus());
                m.put("images", acc.getImages());
                m.put("is_featured", acc.getIsFeatured());
                m.put("view_count", acc.getViewCount());
                m.put("created_at", acc.getCreatedAt());
                m.put("category_name", acc.getCategory().getName());
                m.put("seller_name", acc.getSeller().getUsername());
                data.add(m);
            }
            
            response.put("success", true);
            response.put("data", data);
            response.put("total", total);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/accounts")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminCreateAccount(@RequestBody AdminCreateAccountRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            User seller = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));
            Category category = categoryRepository.findById(req.getCategory_id())
                    .orElseThrow(() -> new RuntimeException("Danh mục không tồn tại"));

            String imagesJson = "[]";
            if (req.getImages() != null && !req.getImages().isEmpty()) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < req.getImages().size(); i++) {
                    sb.append("\"").append(req.getImages().get(i)).append("\"");
                    if (i < req.getImages().size() - 1) sb.append(",");
                }
                sb.append("]");
                imagesJson = sb.toString();
            }

            Account account = Account.builder()
                    .seller(seller)
                    .category(category)
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .price(req.getPrice())
                    .originalPrice(req.getOriginal_price())
                    .username(req.getUsername())
                    .password(req.getPassword())
                    .emailAcc(req.getEmail_acc())
                    .emailPass(req.getEmail_pass())
                    .server(req.getServer())
                    .level(req.getLevel())
                    .rankLevel(req.getRank_level())
                    .championsCount(req.getChampions_count() != null ? req.getChampions_count() : 0)
                    .skinsCount(req.getSkins_count() != null ? req.getSkins_count() : 0)
                    .securityStatus(req.getSecurity_status() != null ? req.getSecurity_status() : "TRANG_THONG_THIN")
                    .status("SHOWING")
                    .images(imagesJson)
                    .isFeatured(req.getIs_featured() != null && req.getIs_featured())
                    .build();

            account = accountRepository.save(account);

            response.put("success", true);
            response.put("message", "Thêm acc thành công");
            response.put("id", account.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/accounts/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminUpdateAccount(@PathVariable("id") Integer id, @RequestBody Map<String, Object> req) {
        Map<String, Object> response = new HashMap<>();
        try {
            Account account = accountRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy acc"));
            String oldStatus = account.getStatus();
            String newStatus = req.containsKey("status") ? (String) req.get("status") : null;

            if (req.containsKey("title")) account.setTitle((String) req.get("title"));
            if (req.containsKey("description")) account.setDescription((String) req.get("description"));
            if (req.containsKey("price")) account.setPrice(new BigDecimal(req.get("price").toString()));
            if (req.containsKey("original_price")) {
                account.setOriginalPrice(req.get("original_price") != null ? new BigDecimal(req.get("original_price").toString()) : null);
            }
            if (req.containsKey("username")) account.setUsername((String) req.get("username"));
            if (req.containsKey("password")) account.setPassword((String) req.get("password"));
            if (req.containsKey("email_acc")) account.setEmailAcc((String) req.get("email_acc"));
            if (req.containsKey("email_pass")) account.setEmailPass((String) req.get("email_pass"));
            if (req.containsKey("server")) account.setServer((String) req.get("server"));
            if (req.containsKey("level")) {
                account.setLevel(req.get("level") != null ? ((Number) req.get("level")).intValue() : null);
            }
            if (req.containsKey("rank_level")) account.setRankLevel((String) req.get("rank_level"));
            if (req.containsKey("champions_count")) {
                account.setChampionsCount(((Number) req.get("champions_count")).intValue());
            }
            if (req.containsKey("skins_count")) {
                account.setSkinsCount(((Number) req.get("skins_count")).intValue());
            }
            if (req.containsKey("security_status")) account.setSecurityStatus((String) req.get("security_status"));
            if (req.containsKey("status")) account.setStatus((String) req.get("status"));
            if (req.containsKey("is_featured")) account.setIsFeatured((Boolean) req.get("is_featured"));

            if (newStatus != null && "REJECTED".equalsIgnoreCase(newStatus) && !"REJECTED".equalsIgnoreCase(oldStatus)) {
                Optional<Transaction> postTxOpt = transactionRepository.findFirstByReferenceIdAndType(account.getId(), "POST_FEE");
                if (postTxOpt.isPresent()) {
                    Transaction postFeeTx = postTxOpt.get();
                    BigDecimal refundAmount = postFeeTx.getAmount();
                    
                    User seller = account.getSeller();
                    BigDecimal beforeBalance = seller.getBalance();
                    seller.setBalance(beforeBalance.add(refundAmount));
                    userRepository.save(seller);
                    
                    Transaction refundTx = Transaction.builder()
                            .user(seller)
                            .amount(refundAmount)
                            .type("REFUND_FEE")
                            .balanceBefore(beforeBalance)
                            .balanceAfter(seller.getBalance())
                            .description("Hoàn phí từ chối duyệt acc: " + account.getTitle())
                            .referenceId(account.getId())
                            .build();
                    transactionRepository.save(refundTx);
                }
            }

            if (req.containsKey("images")) {
                List<String> imgs = (List<String>) req.get("images");
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < imgs.size(); i++) {
                    sb.append("\"").append(imgs.get(i)).append("\"");
                    if (i < imgs.size() - 1) sb.append(",");
                }
                sb.append("]");
                account.setImages(sb.toString());
            }

            accountRepository.save(account);
            response.put("success", true);
            response.put("message", "Cập nhật acc thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/accounts/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminDeleteAccount(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Account account = accountRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy acc"));
            account.setStatus("HIDDEN");
            accountRepository.save(account);
            
            response.put("success", true);
            response.put("message", "Đã ẩn acc thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Skins Management
    @GetMapping("/skins")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> adminGetSkins(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "search", required = false) String search
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            StringBuilder sql = new StringBuilder("SELECT s.* FROM skins s ");
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM skins s ");
            
            List<String> wheres = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            
            if (search != null && !search.trim().isEmpty()) {
                wheres.add("(s.champion_name LIKE :search OR s.skin_name LIKE :search)");
                params.put("search", "%" + search + "%");
            }
            
            if (!wheres.isEmpty()) {
                String wheresStr = " WHERE " + String.join(" AND ", wheres);
                sql.append(wheresStr);
                countSql.append(wheresStr);
            }
            
            sql.append(" ORDER BY s.id DESC");
            
            int offset = (page - 1) * limit;
            var query = entityManager.createNativeQuery(sql.toString(), Skin.class);
            var countQuery = entityManager.createNativeQuery(countSql.toString());
            
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            
            List<Skin> skins = query.getResultList();
            long total = ((Number) countQuery.getSingleResult()).longValue();
            
            response.put("success", true);
            response.put("data", skins);
            response.put("total", total);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/skins")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminCreateSkin(@RequestBody SkinRequest req) {
        Map<String, Object> response = new HashMap<>();
        if (req.getChampion_name() == null || req.getSkin_name() == null || req.getImage_url() == null) {
            response.put("success", false);
            response.put("message", "Vui lòng cung cấp Tên tướng, Tên skin và Ảnh skin");
            return ResponseEntity.badRequest().body(response);
        }
        Skin skin = Skin.builder()
                .championName(req.getChampion_name())
                .skinName(req.getSkin_name())
                .imageUrl(req.getImage_url())
                .build();
        skin = skinRepository.save(skin);
        
        response.put("success", true);
        response.put("message", "Thêm skin thành công");
        response.put("id", skin.getId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/skins/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminUpdateSkin(@PathVariable("id") Integer id, @RequestBody SkinRequest req) {
        Map<String, Object> response = new HashMap<>();
        try {
            Skin skin = skinRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Skin not found"));
            if (req.getChampion_name() != null) skin.setChampionName(req.getChampion_name());
            if (req.getSkin_name() != null) skin.setSkinName(req.getSkin_name());
            if (req.getImage_url() != null) skin.setImageUrl(req.getImage_url());
            skinRepository.save(skin);
            
            response.put("success", true);
            response.put("message", "Cập nhật skin thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/skins/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminDeleteSkin(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            skinRepository.deleteById(id);
            response.put("success", true);
            response.put("message", "Đã xóa skin thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Escrow Orders
    @GetMapping("/orders")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> adminGetOrders(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            StringBuilder sql = new StringBuilder("SELECT o.* FROM orders o " +
                    "JOIN users b ON o.buyer_id = b.id " +
                    "JOIN users s ON o.seller_id = s.id " +
                    "JOIN accounts a ON o.account_id = a.id ");
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM orders o " +
                    "JOIN users b ON o.buyer_id = b.id " +
                    "JOIN users s ON o.seller_id = s.id " +
                    "JOIN accounts a ON o.account_id = a.id ");
            
            List<String> wheres = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            
            if (status != null && !status.trim().isEmpty()) {
                wheres.add("o.status = :status");
                params.put("status", status);
            }
            
            if (search != null && !search.trim().isEmpty()) {
                wheres.add("(b.username LIKE :search OR s.username LIKE :search OR a.title LIKE :search OR CAST(o.id AS CHAR) LIKE :search)");
                params.put("search", "%" + search + "%");
            }
            
            if (!wheres.isEmpty()) {
                String wheresStr = " WHERE " + String.join(" AND ", wheres);
                sql.append(wheresStr);
                countSql.append(wheresStr);
            }
            
            sql.append(" ORDER BY o.created_at DESC");
            
            int offset = (page - 1) * limit;
            var query = entityManager.createNativeQuery(sql.toString(), Order.class);
            var countQuery = entityManager.createNativeQuery(countSql.toString());
            
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            
            List<Order> orders = query.getResultList();
            long total = ((Number) countQuery.getSingleResult()).longValue();
            
            List<Map<String, Object>> list = new ArrayList<>();
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
                map.put("account_title", o.getAccount().getTitle());
                map.put("buyer_name", o.getBuyer().getUsername());
                map.put("seller_name", o.getSeller().getUsername());
                list.add(map);
            }
            response.put("success", true);
            response.put("data", list);
            response.put("total", total);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Deposits
    @GetMapping("/deposits")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> adminGetDeposits(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "search", required = false) String search
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            StringBuilder sql = new StringBuilder("SELECT d.* FROM deposit_requests d JOIN users u ON d.user_id = u.id ");
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM deposit_requests d JOIN users u ON d.user_id = u.id ");
            
            List<String> wheres = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            if (status != null && !status.trim().isEmpty()) {
                wheres.add("d.status = :status");
                params.put("status", status);
            }
            if (search != null && !search.trim().isEmpty()) {
                wheres.add("(u.username LIKE :search OR u.email LIKE :search OR d.transaction_ref LIKE :search)");
                params.put("search", "%" + search + "%");
            }
            if (!wheres.isEmpty()) {
                String wheresStr = " WHERE " + String.join(" AND ", wheres);
                sql.append(wheresStr);
                countSql.append(wheresStr);
            }
            sql.append(" ORDER BY d.created_at DESC");
            
            int offset = (page - 1) * limit;
            var query = entityManager.createNativeQuery(sql.toString(), DepositRequest.class);
            var countQuery = entityManager.createNativeQuery(countSql.toString());
            
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            
            List<DepositRequest> deposits = query.getResultList();
            long total = ((Number) countQuery.getSingleResult()).longValue();
            
            List<Map<String, Object>> list = new ArrayList<>();
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
                map.put("username", d.getUser().getUsername());
                map.put("email", d.getUser().getEmail());
                map.put("full_name", d.getUser().getFullName());
                list.add(map);
            }
            
            response.put("success", true);
            response.put("data", list);
            response.put("total", total);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/deposits/{id}/approve")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminApproveDeposit(@PathVariable("id") Integer id, @RequestBody NoteRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            DepositRequest deposit = depositRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu pending"));
            
            if (!"pending".equals(deposit.getStatus())) {
                response.put("success", false);
                response.put("message", "Yêu cầu này đã được xử lý");
                return ResponseEntity.badRequest().body(response);
            }

            User admin = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            // Update request
            deposit.setStatus("approved");
            deposit.setProcessedBy(admin);
            deposit.setProcessedAt(LocalDateTime.now());
            deposit.setAdminNote(req.getAdmin_note());
            depositRequestRepository.save(deposit);

            // Update user balance
            User user = deposit.getUser();
            BigDecimal balanceBefore = user.getBalance();
            user.setBalance(balanceBefore.add(deposit.getAmount()));
            userRepository.save(user);

            // Write transaction log
            Transaction tx = Transaction.builder()
                    .user(user)
                    .amount(deposit.getAmount())
                    .type("DEPOSIT")
                    .balanceBefore(balanceBefore)
                    .balanceAfter(user.getBalance())
                    .description("Nạp tiền duyệt bởi Admin")
                    .referenceId(deposit.getId())
                    .build();
            transactionRepository.save(tx);

            response.put("success", true);
            response.put("message", "Đã duyệt nạp tiền thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/deposits/{id}/reject")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminRejectDeposit(@PathVariable("id") Integer id, @RequestBody NoteRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            DepositRequest deposit = depositRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu pending"));
            
            if (!"pending".equals(deposit.getStatus())) {
                response.put("success", false);
                response.put("message", "Yêu cầu này đã được xử lý");
                return ResponseEntity.badRequest().body(response);
            }

            User admin = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            deposit.setStatus("rejected");
            deposit.setProcessedBy(admin);
            deposit.setProcessedAt(LocalDateTime.now());
            deposit.setAdminNote(req.getAdmin_note() != null ? req.getAdmin_note() : "Từ chối");
            depositRequestRepository.save(deposit);

            response.put("success", true);
            response.put("message", "Đã từ chối yêu cầu nạp tiền");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Revenue statistics
    @GetMapping("/revenue")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> adminGetRevenue() {
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. Phí giao dịch trung gian (3% từ các đơn hàng hoàn tất)
            String sqlFee = "SELECT SUM(fee) as total FROM orders WHERE status='COMPLETED'";
            Object feeResult = entityManager.createNativeQuery(sqlFee).getSingleResult();
            BigDecimal middlemanRevenue = feeResult != null ? new BigDecimal(feeResult.toString()) : BigDecimal.ZERO;

            // 2. Phí đăng tin của người bán (tổng từ transactions type='POST_FEE')
            String sqlPost = "SELECT IFNULL(SUM(amount), 0) as total FROM transactions WHERE type='POST_FEE'";
            Object postResult = entityManager.createNativeQuery(sqlPost).getSingleResult();
            BigDecimal postingRevenue = postResult != null ? new BigDecimal(postResult.toString()) : BigDecimal.ZERO;

            BigDecimal totalRevenue = middlemanRevenue.add(postingRevenue);

            // 3. Total deposits approved
            String sqlTotalDep = "SELECT SUM(amount) FROM deposit_requests WHERE status='approved'";
            Object totalDepResult = entityManager.createNativeQuery(sqlTotalDep).getSingleResult();
            BigDecimal totalDeposits = totalDepResult != null ? new BigDecimal(totalDepResult.toString()) : BigDecimal.ZERO;

            // 4. Pending deposits counts & sum
            String sqlPendingDep = "SELECT COUNT(*), SUM(amount) FROM deposit_requests WHERE status='pending'";
            Object[] pendingResult = (Object[]) entityManager.createNativeQuery(sqlPendingDep).getSingleResult();
            long pendingCount = ((Number) pendingResult[0]).longValue();
            BigDecimal pendingSum = pendingResult[1] != null ? new BigDecimal(pendingResult[1].toString()) : BigDecimal.ZERO;
            
            Map<String, Object> pendingDeposits = new HashMap<>();
            pendingDeposits.put("count", pendingCount);
            pendingDeposits.put("total", pendingSum);

            // 5. Monthly breakdown combining completed order fee and posting fee
            String monthlySql = "SELECT month, SUM(amount) as revenue, SUM(orders_count) as orders " +
                    "FROM ( " +
                    "  SELECT DATE_FORMAT(created_at, '%Y-%m') as month, SUM(fee) as amount, COUNT(*) as orders_count " +
                    "  FROM orders WHERE status='COMPLETED' GROUP BY month " +
                    "  UNION ALL " +
                    "  SELECT DATE_FORMAT(created_at, '%Y-%m') as month, SUM(amount) as amount, COUNT(*) as orders_count " +
                    "  FROM transactions WHERE type='POST_FEE' GROUP BY month " +
                    ") combined GROUP BY month ORDER BY month DESC LIMIT 12";
            List<Object[]> monthlyRows = entityManager.createNativeQuery(monthlySql).getResultList();
            List<Map<String, Object>> monthly = new ArrayList<>();
            for (Object[] r : monthlyRows) {
                Map<String, Object> m = new HashMap<>();
                m.put("month", r[0]);
                m.put("revenue", r[1]);
                m.put("orders", r[2]);
                monthly.add(m);
            }

            // 6. Top depositors list
            String topUsersSql = "SELECT id, username, full_name, " +
                    "IFNULL((SELECT SUM(amount) FROM deposit_requests WHERE user_id = users.id AND status = 'approved'), 0) as total_deposited " +
                    "FROM users WHERE role = 'user' ORDER BY total_deposited DESC LIMIT 10";
            List<Object[]> topUsersRows = entityManager.createNativeQuery(topUsersSql).getResultList();
            List<Map<String, Object>> topUsers = new ArrayList<>();
            for (Object[] r : topUsersRows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r[0]);
                m.put("username", r[1]);
                m.put("full_name", r[2]);
                m.put("total_deposited", r[3]);
                topUsers.add(m);
            }

            // 7. Counters
            long totalUsers = userRepository.findAll().stream().filter(u -> "user".equalsIgnoreCase(u.getRole())).count();
            
            long totalAccounts = accountRepository.count();
            long availableAccounts = accountRepository.findAll().stream().filter(a -> "SHOWING".equalsIgnoreCase(a.getStatus())).count();
            
            Map<String, Object> totalAccountsMap = new HashMap<>();
            totalAccountsMap.put("total", totalAccounts);
            totalAccountsMap.put("available", availableAccounts);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRevenue", totalRevenue);
            stats.put("orderRevenue", middlemanRevenue);
            stats.put("blindBagRevenue", postingRevenue); // Matches key name expected by frontend dashboard (renamed)
            stats.put("totalDeposits", totalDeposits);
            stats.put("pendingDeposits", pendingDeposits);
            stats.put("monthly", monthly);
            stats.put("topUsers", topUsers);
            stats.put("totalUsers", totalUsers);
            stats.put("totalAccounts", totalAccountsMap);

            response.put("success", true);
            response.put("data", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/users")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> adminGetUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "search", required = false) String search
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            StringBuilder sql = new StringBuilder("SELECT id, username, email, full_name, phone_zalo, balance, frozen_balance, role, status, created_at, " +
                    "IFNULL((SELECT SUM(amount) FROM deposit_requests WHERE user_id = users.id AND status = 'approved'), 0) as total_deposited " +
                    "FROM users ");
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM users ");
            
            List<String> wheres = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            if (search != null && !search.trim().isEmpty()) {
                wheres.add("(username LIKE :search OR email LIKE :search OR full_name LIKE :search)");
                params.put("search", "%" + search + "%");
            }
            
            if (!wheres.isEmpty()) {
                String wheresStr = " WHERE " + String.join(" AND ", wheres);
                sql.append(wheresStr);
                countSql.append(wheresStr);
            }
            sql.append(" ORDER BY created_at DESC");
            
            int offset = (page - 1) * limit;
            var query = entityManager.createNativeQuery(sql.toString());
            var countQuery = entityManager.createNativeQuery(countSql.toString());
            
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            
            List<Object[]> rows = query.getResultList();
            long total = ((Number) countQuery.getSingleResult()).longValue();
            
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object[] row : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("username", row[1]);
                m.put("email", row[2]);
                m.put("full_name", row[3]);
                m.put("phone_zalo", row[4]);
                m.put("balance", row[5]);
                m.put("frozen_balance", row[6]);
                m.put("role", row[7]);
                m.put("status", row[8]);
                m.put("created_at", row[9]);
                m.put("total_deposited", row[10]);
                list.add(m);
            }
            
            response.put("success", true);
            response.put("data", list);
            response.put("total", total);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/history")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> adminGetHistory(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "30") int limit,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "search", required = false) String search
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            StringBuilder sql = new StringBuilder("SELECT t.*, u.username FROM transactions t JOIN users u ON t.user_id = u.id ");
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM transactions t JOIN users u ON t.user_id = u.id ");
            
            List<String> wheres = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            if (type != null && !type.trim().isEmpty()) {
                wheres.add("t.type = :type");
                params.put("type", type);
            }
            if (search != null && !search.trim().isEmpty()) {
                wheres.add("(u.username LIKE :search OR t.description LIKE :search OR CAST(t.id AS CHAR) LIKE :search OR t.reference_id LIKE :search)");
                params.put("search", "%" + search + "%");
            }
            
            if (!wheres.isEmpty()) {
                String wheresStr = " WHERE " + String.join(" AND ", wheres);
                sql.append(wheresStr);
                countSql.append(wheresStr);
            }
            sql.append(" ORDER BY t.created_at DESC");
            
            int offset = (page - 1) * limit;
            var query = entityManager.createNativeQuery(sql.toString(), Transaction.class);
            var countQuery = entityManager.createNativeQuery(countSql.toString());
            
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            
            List<Transaction> transactions = query.getResultList();
            long total = ((Number) countQuery.getSingleResult()).longValue();
            
            List<Map<String, Object>> data = new ArrayList<>();
            for (Transaction t : transactions) {
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
                map.put("username", t.getUser().getUsername());
                data.add(map);
            }
            
            response.put("success", true);
            response.put("data", data);
            response.put("total", total);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Mock News management endpoints to keep admin dashboard happy
    @GetMapping("/news")
    public ResponseEntity<Map<String, Object>> adminGetNews() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Collections.emptyList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/news")
    public ResponseEntity<Map<String, Object>> adminCreateNews(@RequestBody Map<String, Object> req) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Tạo bài viết thành công");
        response.put("id", 1);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/news/{id}")
    public ResponseEntity<Map<String, Object>> adminUpdateNews(@PathVariable("id") Integer id, @RequestBody Map<String, Object> req) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Cập nhật bài viết thành công");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/news/{id}")
    public ResponseEntity<Map<String, Object>> adminDeleteNews(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã xóa bài viết");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/ban")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminBanUser(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
            
            if ("admin".equalsIgnoreCase(user.getRole())) {
                response.put("success", false);
                response.put("message", "Không thể khóa tài khoản Admin");
                return ResponseEntity.badRequest().body(response);
            }

            if ("banned".equalsIgnoreCase(user.getStatus())) {
                response.put("success", false);
                response.put("message", "Tài khoản này đã bị khóa");
                return ResponseEntity.badRequest().body(response);
            }

            // Move balance to frozen_balance
            BigDecimal currentBalance = user.getBalance();
            user.setFrozenBalance(user.getFrozenBalance().add(currentBalance));
            user.setBalance(BigDecimal.ZERO);
            user.setStatus("banned");
            userRepository.save(user);

            // Write transaction log for freezing funds
            if (currentBalance.compareTo(BigDecimal.ZERO) > 0) {
                Transaction tx = Transaction.builder()
                        .user(user)
                        .amount(currentBalance)
                        .type("MIDDLEMAN_HOLD")
                        .balanceBefore(currentBalance)
                        .balanceAfter(BigDecimal.ZERO)
                        .description("Đóng băng toàn bộ số dư tài khoản bị khóa vĩnh viễn")
                        .build();
                transactionRepository.save(tx);
            }

            response.put("success", true);
            response.put("message", "Đã khóa tài khoản vĩnh viễn và đóng băng số dư");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/users/{id}/reset-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminResetPassword(@PathVariable("id") Integer id, @RequestBody Map<String, String> req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
            
            String newPassword = req.get("password");
            if (newPassword == null || newPassword.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Vui lòng nhập mật khẩu mới");
                return ResponseEntity.badRequest().body(response);
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            response.put("success", true);
            response.put("message", "Đã cấp lại mật khẩu cho user " + user.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ==========================================
    // CATEGORY CRUD
    // ==========================================

    @Data
    public static class CategoryRequest {
        private String name;
        private String slug;
        private String image;
        private String description;
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> adminGetCategories() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Category> cats = categoryRepository.findAll();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Category c : cats) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", c.getId());
                m.put("name", c.getName());
                m.put("slug", c.getSlug());
                m.put("image", c.getImage());
                m.put("description", c.getDescription());
                list.add(m);
            }
            response.put("success", true);
            response.put("data", list);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/categories")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminCreateCategory(@RequestBody CategoryRequest req) {
        Map<String, Object> response = new HashMap<>();
        if (req.getName() == null || req.getSlug() == null) {
            response.put("success", false);
            response.put("message", "Tên và slug danh mục không được để trống");
            return ResponseEntity.badRequest().body(response);
        }
        try {
            Category cat = Category.builder()
                    .name(req.getName())
                    .slug(req.getSlug())
                    .image(req.getImage())
                    .description(req.getDescription())
                    .build();
            cat = categoryRepository.save(cat);
            response.put("success", true);
            response.put("message", "Tạo danh mục thành công");
            response.put("id", cat.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminUpdateCategory(
            @PathVariable("id") Integer id, @RequestBody CategoryRequest req) {
        Map<String, Object> response = new HashMap<>();
        Category cat = categoryRepository.findById(id).orElse(null);
        if (cat == null) {
            response.put("success", false);
            response.put("message", "Không tìm thấy danh mục");
            return ResponseEntity.status(404).body(response);
        }
        if (req.getName() != null) cat.setName(req.getName());
        if (req.getSlug() != null) cat.setSlug(req.getSlug());
        if (req.getImage() != null) cat.setImage(req.getImage());
        if (req.getDescription() != null) cat.setDescription(req.getDescription());
        categoryRepository.save(cat);
        response.put("success", true);
        response.put("message", "Cập nhật danh mục thành công");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminDeleteCategory(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        if (!categoryRepository.existsById(id)) {
            response.put("success", false);
            response.put("message", "Không tìm thấy danh mục");
            return ResponseEntity.status(404).body(response);
        }
        categoryRepository.deleteById(id);
        response.put("success", true);
        response.put("message", "Đã xóa danh mục thành công");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> adminGetSettings() {
        Map<String, Object> response = new HashMap<>();
        try {
            String sql = "SELECT setting_key, setting_value FROM system_settings";
            List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
            Map<String, String> settings = new HashMap<>();
            for (Object[] row : rows) {
                settings.put(row[0].toString(), row[1].toString());
            }
            if (!settings.containsKey("post_fee_percent")) {
                settings.put("post_fee_percent", "1.0");
            }
            if (!settings.containsKey("post_fee_max")) {
                settings.put("post_fee_max", "30000");
            }
            response.put("success", true);
            response.put("data", settings);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/settings")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminUpdateSettings(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            for (Map.Entry<String, String> entry : payload.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue() == null ? "" : entry.getValue();
                
                String updateSql = "UPDATE system_settings SET setting_value = ? WHERE setting_key = ?";
                int rowsUpdated = entityManager.createNativeQuery(updateSql)
                        .setParameter(1, val)
                        .setParameter(2, key)
                        .executeUpdate();
                
                if (rowsUpdated == 0) {
                    String insertSql = "INSERT INTO system_settings (setting_key, setting_value) VALUES (?, ?)";
                    entityManager.createNativeQuery(insertSql)
                            .setParameter(1, key)
                            .setParameter(2, val)
                            .executeUpdate();
                }
            }
            response.put("success", true);
            response.put("message", "Cập nhật cấu hình thành công!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
     }

    @PostMapping("/test-telegram")
    public ResponseEntity<Map<String, Object>> adminTestTelegram(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String botToken = payload.get("telegram_bot_token");
            String chatId = payload.get("telegram_chat_id");
            String result = telegramService.sendTestNotification(botToken, chatId);
            if ("Thành công!".equals(result)) {
                response.put("success", true);
                response.put("message", "Gửi tin nhắn thử nghiệm thành công!");
            } else {
                response.put("success", false);
                response.put("message", result);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
