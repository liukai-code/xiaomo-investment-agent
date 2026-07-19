package com.itlk.myclaudecode.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "account_id", unique = true, nullable = false, length = 20)
    private String accountId;

    @Column(nullable = false)
    private String password;

    @Column(name = "free_token_quota")
    private Long freeTokenQuota = 0L;

    @Column(name = "free_token_used")
    private Long freeTokenUsed = 0L;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
