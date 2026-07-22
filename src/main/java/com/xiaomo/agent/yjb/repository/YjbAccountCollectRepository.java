package com.xiaomo.agent.yjb.repository;

import com.xiaomo.agent.yjb.entity.YjbAccountCollect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YjbAccountCollectRepository extends JpaRepository<YjbAccountCollect, Long> {

    List<YjbAccountCollect> findByUserIdOrderBySyncedAtDesc(Long userId);

    void deleteByUserIdAndAccountId(Long userId, String accountId);
}
