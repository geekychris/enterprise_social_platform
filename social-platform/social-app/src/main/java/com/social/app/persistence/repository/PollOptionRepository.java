package com.social.app.persistence.repository;

import com.social.app.persistence.entity.PollOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PollOptionRepository extends JpaRepository<PollOptionEntity, Long> {

    List<PollOptionEntity> findByPollIdOrderBySortOrder(Long pollId);

    @Query("SELECT MAX(o.id) FROM PollOptionEntity o")
    Long findMaxId();
}
