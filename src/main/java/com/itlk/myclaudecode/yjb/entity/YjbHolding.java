package com.itlk.myclaudecode.yjb.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "yjb_holdings")
public class YjbHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "fund_id", length = 50)
    private String fundId;

    @Column(length = 20)
    private String code;

    @Column(name = "short_name", length = 100)
    private String shortName;

    @Column(precision = 15, scale = 2)
    private BigDecimal money;

    @Column(name = "hold_earn", precision = 15, scale = 2)
    private BigDecimal holdEarn;

    @Column(name = "hold_share", precision = 15, scale = 4)
    private BigDecimal holdShare;

    @Column(name = "hold_cost", precision = 15, scale = 2)
    private BigDecimal holdCost;

    @Column(name = "cost_money", precision = 15, scale = 2)
    private BigDecimal costMoney;

    @Column(name = "hold_day", length = 20)
    private String holdDay;

    @Column(length = 50)
    private String category;

    @Column(name = "market_type", length = 20)
    private String marketType;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) syncedAt = LocalDateTime.now();
    }
}
