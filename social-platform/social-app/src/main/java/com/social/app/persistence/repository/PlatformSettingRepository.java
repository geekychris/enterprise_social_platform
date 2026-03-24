package com.social.app.persistence.repository;

import com.social.app.persistence.entity.PlatformSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformSettingRepository extends JpaRepository<PlatformSettingEntity, String> {
}
