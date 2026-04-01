package com.social.app.persistence.repository;

import com.social.app.persistence.entity.AppEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AppEventRepository extends JpaRepository<AppEventEntity, Long> {
    List<AppEventEntity> findByAppIdAndStatus(Long appId, String status);
    List<AppEventEntity> findByAppIdAndStatusAndNextRetryAtBefore(Long appId, String status, Instant before);
    long countByAppIdAndStatus(Long appId, String status);
}
