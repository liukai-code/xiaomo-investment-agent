package com.xiaomo.agent.notification.repository;

import com.xiaomo.agent.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByCreatedAtDesc();

    List<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Notification> findAllByBroadcastTrueOrIdInOrderByCreatedAtDesc(Collection<Long> ids);

    List<Notification> findAllByBroadcastTrueOrIdInOrderByCreatedAtDesc(Collection<Long> ids, Pageable pageable);

    List<Notification> findAllByBroadcastTrueOrderByCreatedAtDesc(Pageable pageable);

    List<Notification> findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDateTime createdAt, Pageable pageable);

    List<Notification> findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrIdInOrderByCreatedAtDesc(LocalDateTime createdAt, Collection<Long> ids, Pageable pageable);
}
