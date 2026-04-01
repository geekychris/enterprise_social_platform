package com.social.app.persistence.repository;

import com.social.app.persistence.entity.AppInstallationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppInstallationRepository extends JpaRepository<AppInstallationEntity, Long> {
    List<AppInstallationEntity> findByAppIdAndActive(Long appId, boolean active);
    List<AppInstallationEntity> findByInstallTypeAndTargetIdAndActive(String installType, Long targetId, boolean active);
    Optional<AppInstallationEntity> findByAppIdAndInstallTypeAndTargetId(Long appId, String installType, Long targetId);
    List<AppInstallationEntity> findByInstalledBy(Long userId);
}
