package com.social.app.persistence.repository;

import com.social.app.persistence.entity.PollVoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVoteEntity, PollVoteEntity.VoteId> {

    List<PollVoteEntity> findByPollId(Long pollId);

    List<PollVoteEntity> findByPollIdAndUserId(Long pollId, Long userId);

    boolean existsByPollIdAndUserId(Long pollId, Long userId);

    @Query("SELECT pv.optionId, COUNT(pv) FROM PollVoteEntity pv WHERE pv.pollId = :pollId GROUP BY pv.optionId")
    List<Object[]> countVotesByOption(@Param("pollId") Long pollId);
}
