package com.itlk.myclaudecode.yjb.repository;

import com.itlk.myclaudecode.yjb.entity.YjbHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YjbHoldingRepository extends JpaRepository<YjbHolding, Long> {

    List<YjbHolding> findByUserIdAndAccountIdOrderByMoneyDesc(Long userId, String accountId);

    List<YjbHolding> findByUserIdOrderByMoneyDesc(Long userId);

    void deleteByUserIdAndAccountId(Long userId, String accountId);
}
