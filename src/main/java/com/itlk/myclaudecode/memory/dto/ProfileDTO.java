package com.itlk.myclaudecode.memory.dto;

import com.itlk.myclaudecode.memory.entity.MemorySourceType;
import com.itlk.myclaudecode.memory.entity.ProfileCategory;
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
