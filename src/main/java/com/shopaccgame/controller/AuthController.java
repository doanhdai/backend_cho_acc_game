package com.shopaccgame.controller;

import com.shopaccgame.model.User;
import com.shopaccgame.repository.UserRepository;
import com.shopaccgame.security.JwtService;
import com.shopaccgame.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final NetHttpTransport googleTransport = new NetHttpTransport();
    private static final GsonFactory googleJsonFactory = GsonFactory.getDefaultInstance();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${google.client-id}")
    private String googleClientId;

    private void setTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Đặt true ở production khi dùng HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 1 ngày
        response.addCookie(cookie);
    }

    @GetMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Hủy cookie lập tức
        response.addCookie(cookie);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Đăng xuất thành công");
        return ResponseEntity.ok(resp);
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;
        @JsonProperty("full_name")
        private String fullName;
        @JsonProperty("phone_zalo")
        private String phoneZalo;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class GoogleAuthRequest {
        private String token;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req, HttpServletResponse httpServletResponse) {
        Map<String, Object> response = new HashMap<>();
        
        if (req.getUsername() == null || req.getEmail() == null || req.getPassword() == null || req.getPhoneZalo() == null) {
            response.put("success", false);
            response.put("message", "Vui lòng nhập đầy đủ thông tin bắt buộc gồm Username, Email, Mật khẩu và Số điện thoại Zalo");
            return ResponseEntity.badRequest().body(response);
        }

        if (!req.getPhoneZalo().matches("^0[0-9]{9}$")) {
            response.put("success", false);
            response.put("message", "Số điện thoại Zalo không đúng định dạng (phải có 10 chữ số và bắt đầu bằng số 0)");
            return ResponseEntity.badRequest().body(response);
        }

        if (userRepository.existsByUsername(req.getUsername()) || userRepository.existsByEmail(req.getEmail())) {
            response.put("success", false);
            response.put("message", "Username hoặc Email đã tồn tại");
            return ResponseEntity.badRequest().body(response);
        }

        User newUser = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName() != null && !req.getFullName().isEmpty() ? req.getFullName() : req.getUsername())
                .phoneZalo(req.getPhoneZalo())
                .role("user")
                .status("active")
                .build();

        newUser = userRepository.save(newUser);

        String token = jwtService.generateToken(newUser);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", newUser.getId());
        userMap.put("username", newUser.getUsername());
        userMap.put("role", newUser.getRole());
        userMap.put("balance", newUser.getBalance());
        userMap.put("frozen_balance", newUser.getFrozenBalance());
        userMap.put("phone_zalo", newUser.getPhoneZalo());
        userMap.put("full_name", newUser.getFullName());

        setTokenCookie(httpServletResponse, token);

        response.put("success", true);
        response.put("message", "Đăng ký thành công");
        response.put("token", token);
        response.put("user", userMap);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req, HttpServletResponse httpServletResponse) {
        Map<String, Object> response = new HashMap<>();
        
        if (req.getUsername() == null || req.getPassword() == null) {
            response.put("success", false);
            response.put("message", "Vui lòng điền tài khoản và mật khẩu");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<User> userOpt = userRepository.findByUsername(req.getUsername());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(req.getUsername());
        }

        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Tài khoản không tồn tại");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        User user = userOpt.get();
        if ("banned".equalsIgnoreCase(user.getStatus())) {
            response.put("success", false);
            response.put("message", "Tài khoản của bạn đã bị khóa vĩnh viễn do vi phạm quy tắc của sàn.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            response.put("success", false);
            response.put("message", "Mật khẩu không đúng");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = jwtService.generateToken(user);

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

        setTokenCookie(httpServletResponse, token);

        response.put("success", true);
        response.put("message", "Đăng nhập thành công");
        response.put("token", token);
        response.put("user", userMap);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe() {
        Map<String, Object> response = new HashMap<>();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Optional<User> userOpt = userRepository.findById(userPrincipal.getId());
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "User không tồn tại");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        if ("banned".equalsIgnoreCase(user.getStatus())) {
            response.put("success", false);
            response.put("message", "Tài khoản của bạn đã bị khóa vĩnh viễn.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
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
        response.put("user", userMap);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleLogin(@RequestBody GoogleAuthRequest req, HttpServletResponse httpServletResponse) {
        Map<String, Object> response = new HashMap<>();

        if (req.getToken() == null || req.getToken().isEmpty()) {
            response.put("success", false);
            response.put("message", "Token không được để trống");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(googleTransport, googleJsonFactory)
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(req.getToken());
            if (idToken == null) {
                response.put("success", false);
                response.put("message", "Google Token không hợp lệ hoặc đã hết hạn");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            Optional<User> userOpt = userRepository.findByEmail(email);
            User user;

            if (userOpt.isPresent()) {
                user = userOpt.get();
                if ("banned".equalsIgnoreCase(user.getStatus())) {
                    response.put("success", false);
                    response.put("message", "Tài khoản của bạn đã bị khóa vĩnh viễn do vi phạm quy tắc của sàn.");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
            } else {
                String username = email.split("@")[0];
                if (userRepository.existsByUsername(username)) {
                    username = username + "_" + UUID.randomUUID().toString().substring(0, 6);
                }

                user = User.builder()
                        .username(username)
                        .email(email)
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .fullName(name != null ? name : username)
                        .avatar(pictureUrl)
                        .phoneZalo("0000000000") // Mặc định để thỏa mãn database constraint
                        .role("user")
                        .status("active")
                        .build();

                user = userRepository.save(user);
            }

            String jwtToken = jwtService.generateToken(user);

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

            setTokenCookie(httpServletResponse, jwtToken);

            response.put("success", true);
            response.put("message", "Đăng nhập Google thành công");
            response.put("token", jwtToken);
            response.put("user", userMap);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Xác thực Google thất bại: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
