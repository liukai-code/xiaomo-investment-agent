package com.xiaomo.agent.memory.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话摘要实体
 */
@Data
@Entity
@Table(name = "conversation_summaries", indexes = {
        @Index(name = "idx_conv_summaries_conv_id", columnList = "conversationId"),
        @Index(name = "idx_conv_summaries_user_id", columnList = "userId")
})
public class ConversationSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "from_message_id")
    private Long fromMessageId;

    @Column(name = "to_message_id")
    private Long toMessageId;

    @Column(name = "compressed_count")
    private Integer compressedCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
