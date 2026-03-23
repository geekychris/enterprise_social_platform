package com.social.app.persistence.repository;

import com.social.app.persistence.entity.PageMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageMembershipRepository extends JpaRepository<PageMembershipEntity, PageMembershipEntity.PageMembershipId> {

    List<PageMembershipEntity> findByUserId(Long userId);

    List<PageMembershipEntity> findByPageId(Long pageId);

    long countByPageId(Long pageId);

    Optional<PageMembershipEntity> findByUserIdAndPageId(Long userId, Long pageId);

    List<PageMembershipEntity> findByPageIdAndStatus(Long pageId, String status);

    long countByPageIdAndStatus(Long pageId, String status);
}
