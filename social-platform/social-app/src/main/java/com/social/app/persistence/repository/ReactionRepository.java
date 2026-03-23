package com.social.app.persistence.repository;

import com.social.app.persistence.entity.ReactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepository extends JpaRepository<ReactionEntity, Long> {

    List<ReactionEntity> findByTargetId(Long targetId);

    Optional<ReactionEntity> findByTargetIdAndUserId(Long targetId, Long userId);

    long countByTargetId(Long targetId);

    long countByTargetIdAndReactionType(Long targetId, String reactionType);

    @Query("SELECT r.reactionType, COUNT(r) FROM ReactionEntity r WHERE r.targetId = :targetId GROUP BY r.reactionType")
    List<Object[]> countGroupedByReactionType(Long targetId);

    List<ReactionEntity> findTop20ByTargetIdOrderByCreatedAtDesc(Long targetId);

    @Query("SELECT r FROM ReactionEntity r WHERE r.targetId = :targetId AND r.userId IN :userIds")
    List<ReactionEntity> findByTargetIdAndUserIdIn(Long targetId, List<Long> userIds);

    @Query("SELECT MAX(r.id) FROM ReactionEntity r")
    Long findMaxId();
}
