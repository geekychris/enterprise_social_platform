package com.social.app.persistence.repository;

import com.social.app.persistence.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<PostEntity, Long> {

    List<PostEntity> findByAuthorId(Long authorId);

    List<PostEntity> findByTargetIdOrderByCreatedAtDesc(Long targetId);

    List<PostEntity> findByIdInOrderByCreatedAtDesc(List<Long> ids);

    @Query("SELECT p FROM PostEntity p WHERE p.authorId IN :authorIds ORDER BY p.createdAt DESC")
    List<PostEntity> findByAuthorIdInOrderByCreatedAtDesc(List<Long> authorIds);

    @Query("SELECT p FROM PostEntity p WHERE (p.authorId IN :authorIds OR (p.targetType IN :targetTypes AND p.targetId IN :targetIds)) ORDER BY p.createdAt DESC")
    List<PostEntity> findFeedPosts(List<Long> authorIds, List<String> targetTypes, List<Long> targetIds);

    @Query("SELECT p FROM PostEntity p WHERE p.targetId IN :targetIds ORDER BY p.createdAt DESC")
    List<PostEntity> findByTargetIdInOrderByCreatedAtDesc(List<Long> targetIds);

    @Query("SELECT p FROM PostEntity p WHERE p.visibility = 'PUBLIC' AND p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<PostEntity> findRecentPublicPosts(java.time.Instant since, org.springframework.data.domain.Pageable pageable);

    default List<PostEntity> findRecentPublicPosts(java.time.Instant since, int limit) {
        return findRecentPublicPosts(since, org.springframework.data.domain.PageRequest.of(0, limit));
    }

    @Query("SELECT MAX(p.id) FROM PostEntity p")
    Long findMaxId();
}
