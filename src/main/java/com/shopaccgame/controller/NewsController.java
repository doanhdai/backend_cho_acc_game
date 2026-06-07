package com.shopaccgame.controller;

import com.shopaccgame.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNews(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Collections.emptyList());
        response.put("total", 0);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Map<String, Object>> getNewsDetail(@PathVariable("slug") String slug) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Không tìm thấy bài viết");
        return ResponseEntity.status(404).body(response);
    }

    @GetMapping("/top-deposit")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getTopDeposit() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Sort by balance (since total_deposited is not in database schema)
            String sql = "SELECT id, username, full_name, avatar, balance FROM users WHERE role = 'user' ORDER BY balance DESC LIMIT 20";
            List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
            
            List<Map<String, Object>> data = new ArrayList<>();
            for (Object[] row : rows) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("username", row[1]);
                map.put("full_name", row[2]);
                map.put("avatar", row[3]);
                map.put("total_deposited", row[4]); // Map user balance to total_deposited key
                data.add(map);
            }
            
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
