package com.shopaccgame.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal price;

    @Column(name = "original_price", precision = 15, scale = 0)
    private BigDecimal originalPrice;

    @Column(length = 100)
    private String username;

    @Column(length = 100)
    private String password;

    @Column(name = "email_acc", length = 100)
    private String emailAcc;

    @Column(name = "email_pass", length = 100)
    private String emailPass;

    @Column(length = 50)
    private String server;

    private Integer level;

    @Column(name = "rank_level", length = 50)
    private String rankLevel;

    @Builder.Default
    @Column(name = "champions_count")
    private Integer championsCount = 0;

    @Builder.Default
    @Column(name = "skins_count")
    private Integer skinsCount = 0;

    @Builder.Default
    @Column(name = "security_status", length = 50)
    private String securityStatus = "TRANG_THONG_THIN";

    @Builder.Default
    @Column(length = 50)
    private String status = "SHOWING";

    @Column(columnDefinition = "json")
    private String images;

    @Builder.Default
    @Column(name = "is_featured")
    private Boolean isFeatured = false;

    @Builder.Default
    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "account_skins",
        joinColumns = @JoinColumn(name = "account_id"),
        inverseJoinColumns = @JoinColumn(name = "skin_id")
    )
    private Set<Skin> skins;
}
