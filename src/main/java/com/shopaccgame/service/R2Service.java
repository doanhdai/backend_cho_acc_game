package com.shopaccgame.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class R2Service {

    @Value("${r2.account-id}")
    private String accountId;

    @Value("${r2.api-token}")
    private String apiToken;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public String uploadFile(MultipartFile file) throws Exception {
        if (accountId == null || accountId.trim().isEmpty() ||
            apiToken == null || apiToken.trim().isEmpty() ||
            bucketName == null || bucketName.trim().isEmpty()) {
            throw new IllegalStateException("Cloudflare R2 configuration is incomplete in application.yml");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String key = UUID.randomUUID().toString() + extension;

        String url = String.format("https://api.cloudflare.com/client/v4/accounts/%s/r2/buckets/%s/objects/%s",
                accountId.trim(), bucketName.trim(), key);

        String contentType = file.getContentType();
        if (contentType == null || contentType.trim().isEmpty()) {
            contentType = "application/octet-stream";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken.trim())
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

        log.info("Uploading object '{}' to R2 bucket '{}'...", key, bucketName);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String baseUrl = publicUrl;
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                // Fallback URL format if public-url is not configured
                baseUrl = String.format("https://pub-%s.r2.dev/", accountId.trim());
            }
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }
            String finalUrl = baseUrl + key;
            log.info("Upload successful. Public URL: {}", finalUrl);
            return finalUrl;
        } else {
            log.error("Failed to upload file to Cloudflare R2. Status code: {}, Response: {}", response.statusCode(), response.body());
            throw new RuntimeException("Failed to upload file to Cloudflare R2. Service returned status code: " + response.statusCode());
        }
    }
}
