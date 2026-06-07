package com.shopaccgame.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    public String sendTestNotification(String botToken, String chatId) {
        try {
            if (botToken == null || botToken.trim().isEmpty() || chatId == null || chatId.trim().isEmpty()) {
                return "Cấu hình bot token hoặc chat ID bị trống.";
            }

            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            String testMessage = "🔔 <b>[KIỂM TRA CẤU HÌNH BOT TELEGRAM]</b>\nCấu hình kết nối thành công!";
            String jsonPayload = String.format(
                    "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"HTML\"}",
                    chatId,
                    testMessage.replace("\"", "\\\"").replace("\n", "\\n")
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "Lỗi từ Telegram (HTTP " + response.statusCode() + "): " + response.body();
            }
            return "Thành công!";
        } catch (Exception e) {
            return "Lỗi kết nối: " + e.getMessage();
        }
    }

    public void sendNotification(String message) {
        new Thread(() -> {
            try {
                String botToken = "";
                String chatId = "";

                try {
                    botToken = jdbcTemplate.queryForObject(
                            "SELECT setting_value FROM system_settings WHERE setting_key = 'telegram_bot_token'",
                            String.class
                    );
                    chatId = jdbcTemplate.queryForObject(
                            "SELECT setting_value FROM system_settings WHERE setting_key = 'telegram_chat_id'",
                            String.class
                    );
                } catch (Exception e) {
                    log.warn("Telegram settings not found in database: {}", e.getMessage());
                }

                if (botToken == null || botToken.trim().isEmpty() || chatId == null || chatId.trim().isEmpty()) {
                    log.info("Telegram configuration is incomplete. Skipping message.");
                    return;
                }

                String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                String jsonPayload = String.format(
                        "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"HTML\"}",
                        chatId,
                        message.replace("\"", "\\\"").replace("\n", "\\n")
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.error("Failed to send Telegram message. Code: {}, Body: {}", response.statusCode(), response.body());
                } else {
                    log.info("Telegram notification sent successfully.");
                }
            } catch (Exception e) {
                log.error("Error sending Telegram message", e);
            }
        }).start();
    }
}
