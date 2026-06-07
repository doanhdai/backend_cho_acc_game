package com.shopaccgame.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InitDbController {

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @GetMapping("/api/init-db")
    public ResponseEntity<Map<String, Object>> initDb() {
        Map<String, Object> response = new HashMap<>();
        String url = "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        
        try {
            // 1. Read schema file
            File schemaFile = new File("/Users/admin/Project/Web/Shop_acc_game/backend/database/schema.sql");
            if (!schemaFile.exists()) {
                response.put("success", false);
                response.put("error", "Schema file not found at path: " + schemaFile.getAbsolutePath());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            String schemaSql = Files.readString(schemaFile.toPath());
            
            // 2. Establish connection to MySQL server
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
                 Statement stmt = conn.createStatement()) {
                
                // 3. Recreate database
                stmt.execute("CREATE DATABASE IF NOT EXISTS shop_acc_game");
                stmt.execute("USE shop_acc_game");
                
                // 4. Split and execute SQL statements
                // Splitting by semicolon
                String[] queries = schemaSql.split(";");
                for (String query : queries) {
                    String cleanQuery = query.trim();
                    if (!cleanQuery.isEmpty()) {
                        stmt.execute(cleanQuery);
                    }
                }
            }
            
            response.put("success", true);
            response.put("message", "Database initialized and seeded successfully!");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
