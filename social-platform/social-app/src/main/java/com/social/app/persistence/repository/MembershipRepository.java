package com.social.app.persistence.repository;

import com.social.app.persistence.entity.MembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<MembershipEntity, MembershipEntity.MembershipId> {

    List<MembershipEntity> findByUserId(Long userId);

    List<MembershipEntity> findByGroupId(Long groupId);

    long countByGroupId(Long groupId);

    List<MembershipEntity> findByGroupIdAndStatus(Long groupId, String status);

    Optional<MembershipEntity> findByUserIdAndGroupId(Long userId, Long groupId);

    List<MembershipEntity> findByUserIdAndStatus(Long userId, String status);
}
