package com.shopaccgame.controller;

import com.shopaccgame.service.R2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final R2Service r2Service;

    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (file == null || file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Vui lòng chọn file để upload");
                return ResponseEntity.badRequest().body(response);
            }

            // Validating file type to ensure it is an image
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                response.put("success", false);
                response.put("message", "Chỉ được phép tải lên file ảnh");
                return ResponseEntity.badRequest().body(response);
            }

            // Validating file extension
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null) {
                String lower = originalFilename.toLowerCase();
                if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".png") && !lower.endsWith(".webp") && !lower.endsWith(".gif")) {
                    response.put("success", false);
                    response.put("message", "Chỉ chấp nhận các định dạng ảnh: JPG, JPEG, PNG, WEBP, GIF");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            String url = r2Service.uploadFile(file);

            response.put("success", true);
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi tải ảnh lên Cloudflare R2: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
