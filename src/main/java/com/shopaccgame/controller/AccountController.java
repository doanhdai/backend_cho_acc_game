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
import java.util.*;

import com.shopaccgame.service.TelegramService;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final SkinRepository skinRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TelegramService telegramService;

    @PersistenceContext
    private EntityManager entityManager;

    @Data
    public static class CreateAccountRequest {
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
        private List<Integer> skin_ids;
    }

    @GetMapping("/categories")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getCategories() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Replicate Express LEFT JOIN accounts status='SHOWING' GROUP BY c.id
            String sql = "SELECT c.id, c.name, c.slug, c.image, c.description, COUNT(a.id) as account_count " +
                         "FROM categories c " +
                         "LEFT JOIN accounts a ON a.category_id = c.id AND a.status = 'SHOWING' " +
                         "GROUP BY c.id ORDER BY c.id ASC";
                         
            List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object[] row : rows) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("name", row[1]);
                map.put("slug", row[2]);
                map.put("image", row[3]);
                map.put("description", row[4]);
                map.put("account_count", ((Number) row[5]).longValue());
                list.add(map);
            }

            response.put("success", true);
            response.put("data", list);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/skins")
    public ResponseEntity<Map<String, Object>> getSkins(@RequestParam(value = "champion", required = false) String champion) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Skin> skins;
            if (champion != null && !champion.trim().isEmpty()) {
                skins = skinRepository.searchSkins(champion);
            } else {
                skins = skinRepository.findAll();
            }
            response.put("success", true);
            response.put("data", skins);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/create")
    @Transactional
    public ResponseEntity<Map<String, Object>> createAccount(@RequestBody CreateAccountRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (req.getCategory_id() == null || req.getTitle() == null || req.getPrice() == null) {
            response.put("success", false);
            response.put("message", "Vui lòng điền đầy đủ các thông tin bắt buộc");
            return ResponseEntity.badRequest().body(response);
        }

        // 1. Fetch user to verify and deduct balance
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Tài khoản người dùng không tồn tại"));

        // Spam prevention: max 2 posts per 5 minutes
        long recentPosts = accountRepository.countRecentPosts(user.getId());
        if (recentPosts >= 2) {
            response.put("success", false);
            response.put("message", "Bạn đã đăng tin quá nhanh. Theo quy định, mỗi tài khoản chỉ được đăng tối đa 2 tin trong vòng 5 phút.");
            return ResponseEntity.badRequest().body(response);
        }

        double percent = 1.0;
        try {
            String sqlSetting = "SELECT setting_value FROM system_settings WHERE setting_key = 'post_fee_percent'";
            List<?> results = entityManager.createNativeQuery(sqlSetting).getResultList();
            if (!results.isEmpty()) {
                percent = Double.parseDouble(results.get(0).toString());
            }
        } catch (Exception ex) {
            // fallback to 1.0%
        }

        BigDecimal price = req.getPrice() != null ? req.getPrice() : BigDecimal.ZERO;
        BigDecimal postFee = price.multiply(new BigDecimal(percent)).divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);

        // Apply maximum post fee cap from system settings
        try {
            String sqlMax = "SELECT setting_value FROM system_settings WHERE setting_key = 'post_fee_max'";
            List<?> maxResults = entityManager.createNativeQuery(sqlMax).getResultList();
            if (!maxResults.isEmpty()) {
                BigDecimal maxCap = new BigDecimal(maxResults.get(0).toString().trim());
                if (maxCap.compareTo(BigDecimal.ZERO) > 0 && postFee.compareTo(maxCap) > 0) {
                    postFee = maxCap;
                }
            }
        } catch (Exception ignored) {}

        if (user.getBalance().compareTo(postFee) < 0) {
            response.put("success", false);
            response.put("message", "Số dư ví của bạn không đủ để thanh toán phí đăng bài (" + postFee.intValue() + "đ). Vui lòng nạp thêm tiền.");
            return ResponseEntity.badRequest().body(response);
        }

        // 2. Deduct balance
        BigDecimal balanceBefore = user.getBalance();
        user.setBalance(balanceBefore.subtract(postFee));
        userRepository.save(user);

        // 3. Resolve category
        Category category = categoryRepository.findById(req.getCategory_id())
                .orElseThrow(() -> new RuntimeException("Danh mục game không hợp lệ"));

        // 4. Resolve skins if any
        Set<Skin> skins = new HashSet<>();
        if (req.getSkin_ids() != null && !req.getSkin_ids().isEmpty()) {
            skins.addAll(skinRepository.findAllById(req.getSkin_ids()));
        }

        // 5. Serialize images list to JSON string
        String imagesJson = "[]";
        if (req.getImages() != null && !req.getImages().isEmpty()) {
            // Using a simple JSON format conversion manually to bypass deep dependency requirements
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < req.getImages().size(); i++) {
                sb.append("\"").append(req.getImages().get(i)).append("\"");
                if (i < req.getImages().size() - 1) sb.append(",");
            }
            sb.append("]");
            imagesJson = sb.toString();
        }

        // 6. Save Listing
        Account account = Account.builder()
                .seller(user)
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
                .status("PENDING_APPROVAL")
                .images(imagesJson)
                .skins(skins)
                .build();

        account = accountRepository.save(account);

        // 7. Write transaction log
        Transaction transaction = Transaction.builder()
                .user(user)
                .amount(postFee)
                .type("POST_FEE")
                .balanceBefore(balanceBefore)
                .balanceAfter(user.getBalance())
                .description("Phí đăng tin bán acc: " + req.getTitle())
                .referenceId(account.getId())
                .build();
        transactionRepository.save(transaction);

        // Send Telegram notification
        try {
            String msg = String.format(
                    "🔔 <b>[TIN ĐĂNG MỚI]</b>\n" +
                    "- ID: #%d\n" +
                    "- Tiêu đề: %s\n" +
                    "- Giá: %sđ\n" +
                    "- Danh mục: %s\n" +
                    "- Người bán: %s\n" +
                    "- Zalo: %s\n" +
                    "👉 Vui lòng truy cập admin để duyệt tin.",
                    account.getId(),
                    telegramService.escapeHtml(account.getTitle()),
                    req.getPrice() != null ? req.getPrice().toString() : "0",
                    telegramService.escapeHtml(category.getName()),
                    telegramService.escapeHtml(user.getUsername()),
                    telegramService.escapeHtml(user.getPhoneZalo() != null ? user.getPhoneZalo() : "Chưa cập nhật")
            );
            telegramService.sendNotification(msg);
        } catch (Exception ex) {
            // Ignore error so registration does not fail
        }

        response.put("success", true);
        response.put("message", "Đăng tin thành công và đang chờ duyệt!");
        response.put("accountId", account.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAccounts(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "price_min", required = false) Double priceMin,
            @RequestParam(value = "price_max", required = false) Double priceMax,
            @RequestParam(value = "rank", required = false) String rank,
            @RequestParam(value = "rank_level", required = false) String rankLevel,
            @RequestParam(value = "skin_ids", required = false) String skinIdsStr,
            @RequestParam(value = "sort", defaultValue = "created_at") String sort,
            @RequestParam(value = "order", defaultValue = "DESC") String order,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "12") int limit
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Integer> skinIds = new ArrayList<>();
            if (skinIdsStr != null && !skinIdsStr.trim().isEmpty()) {
                for (String s : skinIdsStr.split(",")) {
                    try {
                        skinIds.add(Integer.parseInt(s.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            String finalRank = (rank != null && !rank.trim().isEmpty()) ? rank : rankLevel;

            AccountRepositoryCustom.SearchResult result = accountRepository.searchAccounts(
                    category, search, priceMin, priceMax, finalRank, skinIds, sort, order, page, limit
            );

            // Structure response payload to map exactly to the React expectations
            List<Map<String, Object>> data = new ArrayList<>();
            for (Account acc : result.getAccounts()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", acc.getId());
                map.put("title", acc.getTitle());
                map.put("description", acc.getDescription());
                map.put("price", acc.getPrice());
                map.put("original_price", acc.getOriginalPrice());
                map.put("server", acc.getServer());
                map.put("level", acc.getLevel());
                map.put("rank_level", acc.getRankLevel());
                map.put("champions_count", acc.getChampionsCount());
                map.put("skins_count", acc.getSkinsCount());
                map.put("security_status", acc.getSecurityStatus());
                map.put("status", acc.getStatus());
                map.put("images", acc.getImages());
                map.put("is_featured", acc.getIsFeatured());
                map.put("view_count", acc.getViewCount());
                map.put("category_name", acc.getCategory().getName());
                map.put("category_slug", acc.getCategory().getSlug());
                map.put("seller_name", acc.getSeller().getUsername());
                map.put("seller_phone", acc.getSeller().getPhoneZalo());
                data.add(map);
            }

            response.put("success", true);
            response.put("data", data);
            response.put("total", result.getTotal());
            response.put("page", page);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> getAccountById(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Account account = accountRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy acc"));

            // Increment view count
            account.setViewCount(account.getViewCount() + 1);
            accountRepository.save(account);

            // Check permissions
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            boolean isSeller = false;
            boolean isAdmin = false;
            boolean isBuyer = false;

            if (principal instanceof UserPrincipal userPrincipal) {
                isSeller = userPrincipal.getId().equals(account.getSeller().getId());
                isAdmin = "admin".equalsIgnoreCase(userPrincipal.getRole());
                isBuyer = accountRepository.checkBuyer(userPrincipal.getId(), account.getId());
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", account.getId());
            map.put("seller_id", account.getSeller().getId());
            map.put("category_id", account.getCategory().getId());
            map.put("title", account.getTitle());
            map.put("description", account.getDescription());
            map.put("price", account.getPrice());
            map.put("original_price", account.getOriginalPrice());
            map.put("server", account.getServer());
            map.put("level", account.getLevel());
            map.put("rank_level", account.getRankLevel());
            map.put("champions_count", account.getChampionsCount());
            map.put("skins_count", account.getSkinsCount());
            map.put("security_status", account.getSecurityStatus());
            map.put("status", account.getStatus());
            map.put("images", account.getImages());
            map.put("is_featured", account.getIsFeatured());
            map.put("view_count", account.getViewCount());
            map.put("category_name", account.getCategory().getName());
            map.put("category_slug", account.getCategory().getSlug());
            map.put("seller_name", account.getSeller().getUsername());
            map.put("seller_phone", account.getSeller().getPhoneZalo());

            // Mapped skins list
            List<Map<String, Object>> skinsList = new ArrayList<>();
            for (Skin s : account.getSkins()) {
                Map<String, Object> sm = new HashMap<>();
                sm.put("id", s.getId());
                sm.put("champion_name", s.getChampionName());
                sm.put("skin_name", s.getSkinName());
                sm.put("image_url", s.getImageUrl());
                skinsList.add(sm);
            }
            map.put("skins_list", skinsList);

            response.put("success", true);
            response.put("data", map);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/post-fee-percent")
    public ResponseEntity<Map<String, Object>> getPostFeePercent() {
        Map<String, Object> response = new HashMap<>();
        try {
            double percent = 1.0;
            try {
                String sqlSetting = "SELECT setting_value FROM system_settings WHERE setting_key = 'post_fee_percent'";
                List<?> results = entityManager.createNativeQuery(sqlSetting).getResultList();
                if (!results.isEmpty()) {
                    percent = Double.parseDouble(results.get(0).toString());
                }
            } catch (Exception ex) {
                // Ignore and use default 1.0%
            }
            response.put("success", true);
            response.put("percent", percent);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateAccount(@PathVariable("id") Integer id, @RequestBody CreateAccountRequest req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin đăng"));

        if (!account.getSeller().getId().equals(userPrincipal.getId())) {
            response.put("success", false);
            response.put("message", "Bạn không có quyền sửa tin đăng này");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Tài khoản người dùng không tồn tại"));

        String oldStatus = account.getStatus();

        // If listing was rejected, deduct fee again
        if ("REJECTED".equalsIgnoreCase(oldStatus)) {
            double percent = 1.0;
            try {
                String sqlSetting = "SELECT setting_value FROM system_settings WHERE setting_key = 'post_fee_percent'";
                List<?> results = entityManager.createNativeQuery(sqlSetting).getResultList();
                if (!results.isEmpty()) {
                    percent = Double.parseDouble(results.get(0).toString());
                }
            } catch (Exception ex) {
                // fallback to 1.0%
            }

            BigDecimal price = req.getPrice() != null ? req.getPrice() : BigDecimal.ZERO;
            BigDecimal postFee = price.multiply(new BigDecimal(percent)).divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);

            // Apply maximum post fee cap from system settings
            try {
                String sqlMax = "SELECT setting_value FROM system_settings WHERE setting_key = 'post_fee_max'";
                List<?> maxResults = entityManager.createNativeQuery(sqlMax).getResultList();
                if (!maxResults.isEmpty()) {
                    BigDecimal maxCap = new BigDecimal(maxResults.get(0).toString().trim());
                    if (maxCap.compareTo(BigDecimal.ZERO) > 0 && postFee.compareTo(maxCap) > 0) {
                        postFee = maxCap;
                    }
                }
            } catch (Exception ignored) {}

            if (user.getBalance().compareTo(postFee) < 0) {
                response.put("success", false);
                response.put("message", "Số dư ví không đủ để thanh toán lại phí đăng bài (" + postFee.intValue() + "đ).");
                return ResponseEntity.badRequest().body(response);
            }

            BigDecimal balanceBefore = user.getBalance();
            user.setBalance(balanceBefore.subtract(postFee));
            userRepository.save(user);

            Transaction transaction = Transaction.builder()
                    .user(user)
                    .amount(postFee)
                    .type("POST_FEE")
                    .balanceBefore(balanceBefore)
                    .balanceAfter(user.getBalance())
                    .description("Phí đăng lại tin bán acc (sau từ chối): " + req.getTitle())
                    .referenceId(account.getId())
                    .build();
            transactionRepository.save(transaction);
        }

        // Update fields
        Category category = categoryRepository.findById(req.getCategory_id())
                .orElseThrow(() -> new RuntimeException("Danh mục game không hợp lệ"));

        Set<Skin> skins = new HashSet<>();
        if (req.getSkin_ids() != null && !req.getSkin_ids().isEmpty()) {
            skins.addAll(skinRepository.findAllById(req.getSkin_ids()));
        }

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

        account.setCategory(category);
        account.setTitle(req.getTitle());
        account.setDescription(req.getDescription());
        account.setPrice(req.getPrice());
        account.setOriginalPrice(req.getOriginal_price());
        account.setUsername(req.getUsername());
        account.setPassword(req.getPassword());
        account.setEmailAcc(req.getEmail_acc());
        account.setEmailPass(req.getEmail_pass());
        account.setRankLevel(req.getRank_level());
        account.setChampionsCount(req.getChampions_count() != null ? req.getChampions_count() : 0);
        account.setSkinsCount(req.getSkins_count() != null ? req.getSkins_count() : 0);
        account.setSecurityStatus(req.getSecurity_status() != null ? req.getSecurity_status() : "TRANG_THONG_THIN");
        account.setImages(imagesJson);
        account.setSkins(skins);
        
        // Reset status to PENDING_APPROVAL on edit
        account.setStatus("PENDING_APPROVAL");

        accountRepository.save(account);

        // Telegram notification
        try {
            String msg = String.format(
                    "🔔 <b>[TIN ĐĂNG ĐÃ SỬA]</b>\n" +
                    "- ID: #%d\n" +
                    "- Tiêu đề: %s\n" +
                    "- Giá: %sđ\n" +
                    "- Danh mục: %s\n" +
                    "- Người bán: %s\n" +
                    "- Zalo: %s\n" +
                    "👉 Tin đăng đã được sửa đổi và đang chờ duyệt lại.",
                    account.getId(),
                    telegramService.escapeHtml(account.getTitle()),
                    account.getPrice() != null ? account.getPrice().toString() : "0",
                    telegramService.escapeHtml(category.getName()),
                    telegramService.escapeHtml(user.getUsername()),
                    telegramService.escapeHtml(user.getPhoneZalo() != null ? user.getPhoneZalo() : "Chưa cập nhật")
            );
            telegramService.sendNotification(msg);
        } catch (Exception ex) {
            // ignore
        }

        response.put("success", true);
        response.put("message", "Cập nhật tin đăng thành công và chờ duyệt!");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateAccountStatus(@PathVariable("id") Integer id, @RequestBody Map<String, String> req) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin đăng"));

        if (!account.getSeller().getId().equals(userPrincipal.getId())) {
            response.put("success", false);
            response.put("message", "Bạn không có quyền thay đổi trạng thái tin đăng này");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        String newStatus = req.get("status");
        if (newStatus == null || (!"SOLD".equalsIgnoreCase(newStatus) && !"HIDDEN".equalsIgnoreCase(newStatus) && !"SHOWING".equalsIgnoreCase(newStatus))) {
            response.put("success", false);
            response.put("message", "Trạng thái không hợp lệ");
            return ResponseEntity.badRequest().body(response);
        }

        // Keep upper case
        account.setStatus(newStatus.toUpperCase());
        accountRepository.save(account);

        response.put("success", true);
        response.put("message", "Cập nhật trạng thái thành công!");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin đăng"));

        if (!account.getSeller().getId().equals(userPrincipal.getId())) {
            response.put("success", false);
            response.put("message", "Bạn không có quyền xoá tin đăng này");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Soft delete by setting status to DELETED
        account.setStatus("DELETED");
        accountRepository.save(account);

        response.put("success", true);
        response.put("message", "Xoá tin đăng thành công!");
        return ResponseEntity.ok(response);
    }
}
