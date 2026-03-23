package com.social.app.persistence.repository;

import com.social.app.persistence.entity.FollowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FollowRepository extends JpaRepository<FollowEntity, FollowEntity.FollowId> {

    List<FollowEntity> findByFollowerId(Long followerId);

    List<FollowEntity> findByFollowedId(Long followedId);

    boolean existsByFollowerIdAndFollowedId(Long followerId, Long followedId);

    long countByFollowedId(Long followedId);

    long countByFollowerId(Long followerId);
}
