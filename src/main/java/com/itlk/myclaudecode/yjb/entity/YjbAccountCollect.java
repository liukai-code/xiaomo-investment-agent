package com.itlk.myclaudecode.yjb.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "yjb_account_collects")
public class YjbAccountCollect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "hold_cost", precision = 15, scale = 2)
    private BigDecimal holdCost;

    @Column(name = "today_income", precision = 15, scale = 2)
    private BigDecimal todayIncome;

    @Column(name = "today_income_rate", precision = 8, scale = 4)
    private BigDecimal todayIncomeRate;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) syncedAt = LocalDateTime.now();
    }
}
