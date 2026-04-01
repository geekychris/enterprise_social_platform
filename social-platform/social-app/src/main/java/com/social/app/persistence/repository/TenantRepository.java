package com.social.app.persistence.repository;

import com.social.app.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {
    Optional<TenantEntity> findBySlug(String slug);
}
