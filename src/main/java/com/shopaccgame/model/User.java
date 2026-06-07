package com.shopaccgame.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 255)
    private String avatar;

    @Column(name = "phone_zalo", nullable = false, length = 20)
    private String phoneZalo;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "frozen_balance", nullable = false, precision = 15, scale = 0)
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(length = 20)
    private String role = "user";

    @Builder.Default
    @Column(length = 20)
    private String status = "active";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
