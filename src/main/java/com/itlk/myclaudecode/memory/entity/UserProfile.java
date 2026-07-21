package com.itlk.myclaudecode.memory.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户画像记忆实体
 */
@Data
@Entity
@Table(name = "user_profiles", indexes = {
        @Index(name = "idx_user_profiles_user_id", columnList = "userId"),
        @Index(name = "idx_user_profiles_user_category", columnList = "userId, category")
})
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProfileCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 重要性 1-5，5 为最高（用户主动说"记住"的默认为 5） */
    @Column(nullable = false)
    private Integer importance = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private MemorySourceType sourceType;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
