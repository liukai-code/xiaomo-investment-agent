package com.itlk.myclaudecode.memory.repository;

import com.itlk.myclaudecode.memory.entity.ExtractionTaskStatus;
import com.itlk.myclaudecode.memory.entity.MemoryExtractionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryExtractionTaskRepository extends JpaRepository<MemoryExtractionTask, Long> {

    boolean existsByConversationIdAndStatus(Long conversationId, ExtractionTaskStatus status);

    @Query("SELECT t FROM MemoryExtractionTask t WHERE t.conversationId = :convId " +
           "ORDER BY t.createdAt DESC LIMIT 1")
    MemoryExtractionTask findLatestByConversationId(@Param("convId") Long convId);
}
