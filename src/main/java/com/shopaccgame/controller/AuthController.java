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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req) {
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

        response.put("success", true);
        response.put("message", "Đăng ký thành công");
        response.put("token", token);
        response.put("user", userMap);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
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
}
