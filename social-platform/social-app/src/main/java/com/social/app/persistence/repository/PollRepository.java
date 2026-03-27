package com.social.app.persistence.repository;

import com.social.app.persistence.entity.PollEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PollRepository extends JpaRepository<PollEntity, Long> {

    Optional<PollEntity> findByPostId(Long postId);

    @Query("SELECT MAX(p.id) FROM PollEntity p")
    Long findMaxId();
}
