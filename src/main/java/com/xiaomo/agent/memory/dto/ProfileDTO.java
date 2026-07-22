package com.xiaomo.agent.memory.dto;

import com.xiaomo.agent.memory.entity.MemorySourceType;
import com.xiaomo.agent.memory.entity.ProfileCategory;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProfileDTO {

    private Long id;
    private ProfileCategory category;
    private String content;
    private Integer importance;
    private MemorySourceType sourceType;
    private Long conversationId;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
