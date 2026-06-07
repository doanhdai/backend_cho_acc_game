package com.shopaccgame;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            // Create table system_settings if not exists
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS system_settings (" +
                "  setting_key VARCHAR(100) PRIMARY KEY," +
                "  setting_value VARCHAR(255) NOT NULL" +
                ")"
            );

            // Seed default post_fee_percent = 1.0 (1%)
            jdbcTemplate.execute(
                "INSERT IGNORE INTO system_settings (setting_key, setting_value) " +
                "VALUES ('post_fee_percent', '1.0')"
            );
            // Seed default post_fee_max = 30000 (30,000đ)
            jdbcTemplate.execute(
                "INSERT IGNORE INTO system_settings (setting_key, setting_value) " +
                "VALUES ('post_fee_max', '30000')"
            );
            jdbcTemplate.execute(
                "INSERT IGNORE INTO system_settings (setting_key, setting_value) " +
                "VALUES ('telegram_bot_token', '')"
            );
            jdbcTemplate.execute(
                "INSERT IGNORE INTO system_settings (setting_key, setting_value) " +
                "VALUES ('telegram_chat_id', '')"
            );
            
            // Auto alter accounts table status column if needed
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE accounts MODIFY COLUMN status ENUM('SHOWING', 'IN_TRANSACTION', 'SOLD', 'HIDDEN', 'PENDING_APPROVAL', 'REJECTED', 'DELETED') DEFAULT 'PENDING_APPROVAL'"
                );
                log.info("Successfully altered accounts table status column enum to support all listing states.");
            } catch (Exception e) {
                log.warn("Could not alter accounts table status column: {}", e.getMessage());
            }

            // Add FULLTEXT index to accounts table for search optimization
            try {
                String checkIndexSql = "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'accounts' AND index_name = 'idx_accounts_fulltext'";
                Integer count = jdbcTemplate.queryForObject(checkIndexSql, Integer.class);
                if (count == null || count == 0) {
                    jdbcTemplate.execute("ALTER TABLE accounts ADD FULLTEXT INDEX idx_accounts_fulltext (title, description)");
                    log.info("Successfully added FULLTEXT index idx_accounts_fulltext to accounts table.");
                }
            } catch (Exception e) {
                log.warn("Could not add FULLTEXT index to accounts table: {}", e.getMessage());
            }

            // Add BTREE performance indexes
            try {
                String[] indexQueries = {
                    "ALTER TABLE accounts ADD INDEX idx_accounts_status_created (status, created_at)",
                    "ALTER TABLE accounts ADD INDEX idx_accounts_price (price)",
                    "ALTER TABLE accounts ADD INDEX idx_accounts_rank (rank_level)",
                    "ALTER TABLE transactions ADD INDEX idx_transactions_created (created_at)"
                };
                
                for (String query : indexQueries) {
                    try {
                        jdbcTemplate.execute(query);
                    } catch (Exception e) {
                        // Index might already exist, safe to ignore
                    }
                }
                log.info("Successfully checked/added BTREE performance indexes.");
            } catch (Exception e) {
                log.warn("Could not add performance indexes: {}", e.getMessage());
            }

            log.info("System settings initialized successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize system settings table", e);
        }
    }
}
