package com.itlk.myclaudecode.memory.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆提取任务实体（异步任务队列，防重复+重试）
 */
@Data
@Entity
@Table(name = "memory_extraction_tasks", indexes = {
        @Index(name = "idx_mem_task_conv_id", columnList = "conversationId"),
        @Index(name = "idx_mem_task_status", columnList = "status")
})
public class MemoryExtractionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "trigger_message_id", nullable = false)
    private Long triggerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExtractionTaskStatus status = ExtractionTaskStatus.PENDING;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
