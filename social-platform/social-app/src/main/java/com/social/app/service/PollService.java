package com.social.app.service;

import com.social.app.persistence.entity.PollEntity;
import com.social.app.persistence.entity.PollOptionEntity;
import com.social.app.persistence.entity.PollVoteEntity;
import com.social.app.persistence.repository.PollOptionRepository;
import com.social.app.persistence.repository.PollRepository;
import com.social.app.persistence.repository.PollVoteRepository;
import com.social.core.dto.PollDto;
import com.social.core.dto.PollOptionDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final GlobalIdGenerator idGenerator;

    public PollService(PollRepository pollRepository,
                       PollOptionRepository pollOptionRepository,
                       PollVoteRepository pollVoteRepository,
                       GlobalIdGenerator idGenerator) {
        this.pollRepository = pollRepository;
        this.pollOptionRepository = pollOptionRepository;
        this.pollVoteRepository = pollVoteRepository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public PollEntity createPoll(long createdBy, long postId, String question,
                                 List<String> options, boolean allowMultiple, Instant closesAt) {
        PollEntity poll = new PollEntity();
        poll.setId(idGenerator.next(ObjectType.POLL).value());
        poll.setPostId(postId);
        poll.setQuestion(question);
        poll.setAllowMultiple(allowMultiple);
        poll.setClosesAt(closesAt);
        poll.setCreatedBy(createdBy);
        PollEntity saved = pollRepository.save(poll);

        for (short i = 0; i < options.size(); i++) {
            PollOptionEntity option = new PollOptionEntity();
            option.setId(idGenerator.next(ObjectType.POLL_OPTION).value());
            option.setPollId(saved.getId());
            option.setLabel(options.get(i));
            option.setSortOrder(i);
            pollOptionRepository.save(option);
        }

        return saved;
    }

    @Transactional
    public void vote(long userId, long pollId, List<Long> optionIds) {
        PollEntity poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found: " + pollId));

        if (poll.getClosesAt() != null && Instant.now().isAfter(poll.getClosesAt())) {
            throw new IllegalStateException("Poll is closed");
        }

        if (!poll.isAllowMultiple() && optionIds.size() > 1) {
            throw new IllegalArgumentException("This poll does not allow multiple votes");
        }

        // Remove existing votes first if single-choice poll
        if (!poll.isAllowMultiple()) {
            List<PollVoteEntity> existingVotes = pollVoteRepository.findByPollIdAndUserId(pollId, userId);
            pollVoteRepository.deleteAll(existingVotes);
        }

        for (Long optionId : optionIds) {
            PollVoteEntity vote = new PollVoteEntity();
            vote.setPollId(pollId);
            vote.setUserId(userId);
            vote.setOptionId(optionId);
            pollVoteRepository.save(vote);
        }
    }

    @Transactional
    public void removeVote(long userId, long pollId) {
        List<PollVoteEntity> votes = pollVoteRepository.findByPollIdAndUserId(pollId, userId);
        pollVoteRepository.deleteAll(votes);
    }

    public PollDto toDto(long pollId, Long currentUserId) {
        PollEntity poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found: " + pollId));

        List<PollOptionEntity> options = pollOptionRepository.findByPollIdOrderBySortOrder(pollId);

        // Build vote counts per option
        Map<Long, Integer> voteCounts = new HashMap<>();
        for (Object[] row : pollVoteRepository.countVotesByOption(pollId)) {
            Long optionId = (Long) row[0];
            Long count = (Long) row[1];
            voteCounts.put(optionId, count.intValue());
        }

        int totalVotes = voteCounts.values().stream().mapToInt(Integer::intValue).sum();

        List<PollOptionDto> optionDtos = new ArrayList<>();
        for (PollOptionEntity option : options) {
            int count = voteCounts.getOrDefault(option.getId(), 0);
            optionDtos.add(new PollOptionDto(option.getId(), option.getLabel(), count));
        }

        // Get current user's votes
        List<Long> currentUserVotes = List.of();
        if (currentUserId != null) {
            currentUserVotes = pollVoteRepository.findByPollIdAndUserId(pollId, currentUserId)
                    .stream()
                    .map(PollVoteEntity::getOptionId)
                    .toList();
        }

        boolean closed = poll.getClosesAt() != null && Instant.now().isAfter(poll.getClosesAt());

        return new PollDto(
                poll.getId(),
                poll.getQuestion(),
                poll.isAllowMultiple(),
                poll.getClosesAt(),
                closed,
                optionDtos,
                totalVotes,
                currentUserVotes
        );
    }
}
