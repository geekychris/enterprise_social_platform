package com.social.app.persistence.repository;

import com.social.app.persistence.entity.AppEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppRepository extends JpaRepository<AppEntity, Long> {
    Optional<AppEntity> findBySlugAndTenantId(String slug, Long tenantId);
    List<AppEntity> findByAppTypeAndActive(String appType, boolean active);
    List<AppEntity> findByActive(boolean active);
}
