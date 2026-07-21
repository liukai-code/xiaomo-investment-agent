package com.itlk.myclaudecode.memory.repository;

import com.itlk.myclaudecode.memory.entity.ProfileCategory;
import com.itlk.myclaudecode.memory.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    List<UserProfile> findByUserIdAndActiveTrueOrderByImportanceDesc(Long userId);

    List<UserProfile> findByUserIdAndCategoryAndActiveTrue(Long userId, ProfileCategory category);

    @Query("SELECT p FROM UserProfile p WHERE p.userId = :userId AND p.active = true " +
           "ORDER BY p.importance DESC LIMIT :limit")
    List<UserProfile> findTopByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    boolean existsByUserIdAndContent(Long userId, String content);

    @Query("SELECT COUNT(p) FROM UserProfile p WHERE p.userId = :userId AND p.active = true")
    long countActiveByUserId(@Param("userId") Long userId);

    List<UserProfile> findByActiveTrueAndImportanceLessThanEqualAndUpdatedAtBefore(
            Integer importance, LocalDateTime updatedAt);
}
