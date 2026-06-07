package com.shopaccgame.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "deposit_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    @Builder.Default
    @Column(length = 50)
    private String method = "bank_transfer";

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Builder.Default
    @Column(length = 50)
    private String status = "pending";

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
