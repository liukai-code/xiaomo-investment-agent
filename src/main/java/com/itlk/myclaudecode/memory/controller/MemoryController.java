package com.itlk.myclaudecode.memory.controller;

import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.memory.dto.ProfileDTO;
import com.itlk.myclaudecode.memory.entity.ConversationSummary;
import com.itlk.myclaudecode.memory.entity.UserProfile;
import com.itlk.myclaudecode.memory.service.MemoryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/memory")
@Slf4j
public class MemoryController {

    @Resource
    private MemoryService memoryService;

    /** 获取用户所有画像记忆 */
    @GetMapping("/profiles")
    public Result<List<ProfileDTO>> listProfiles(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<UserProfile> profiles = memoryService.getActiveProfiles(userId);
        return Result.success(profiles.stream().map(this::toDTO).toList());
    }

    /** 用户手动添加记忆 */
    @PostMapping("/profiles")
    public Result<ProfileDTO> addProfile(@RequestBody ProfileDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            return Result.error("记忆内容不能为空");
        }
        if (dto.getCategory() == null) {
            return Result.error("记忆类别不能为空");
        }
        UserProfile saved = memoryService.addUserMemory(userId, dto.getContent(),
                dto.getCategory(), dto.getConversationId());
        return Result.success(toDTO(saved));
    }

    /** 更新记忆 */
    @PutMapping("/profiles/{id}")
    public Result<ProfileDTO> updateProfile(@PathVariable Long id, @RequestBody ProfileDTO dto,
                                             HttpServletRequest request) {
        Long userId = getUserId(request);
        UserProfile updated = memoryService.updateProfile(userId, id,
                dto.getContent(), dto.getImportance());
        return Result.success(toDTO(updated));
    }

    /** 删除记忆 */
    @DeleteMapping("/profiles/{id}")
    public Result<Void> deleteProfile(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        memoryService.deleteProfile(userId, id);
        return Result.success(null);
    }

    /** 获取指定会话的摘要 */
    @GetMapping("/summaries/{conversationId}")
    public Result<ConversationSummary> getSummary(@PathVariable Long conversationId,
                                                   HttpServletRequest request) {
        getUserId(request);
        ConversationSummary summary = memoryService.getLatestSummary(conversationId);
        return Result.success(summary);
    }

    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new RuntimeException("未登录");
        return userId;
    }

    private ProfileDTO toDTO(UserProfile entity) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(entity.getId());
        dto.setCategory(entity.getCategory());
        dto.setContent(entity.getContent());
        dto.setImportance(entity.getImportance());
        dto.setSourceType(entity.getSourceType());
        dto.setConversationId(entity.getConversationId());
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
