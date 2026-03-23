package com.social.app.persistence.repository;

import com.social.app.persistence.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    List<CommentEntity> findByPostIdAndDepthOrderByCreatedAtAsc(Long postId, Short depth);

    List<CommentEntity> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId);

    long countByPostId(Long postId);

    @Query("SELECT MAX(c.id) FROM CommentEntity c")
    Long findMaxId();
}
