package com.social.app.service;

import com.social.app.graph.AoeeGraphClient;
import com.social.app.persistence.entity.ReactionEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.persistence.repository.ReactionRepository;
import com.social.core.dto.ReactorDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReactionService {

    private static final Logger log = LoggerFactory.getLogger(ReactionService.class);

    private final ReactionRepository reactionRepository;
    private final FollowRepository followRepository;
    private final UserService userService;
    private final AoeeGraphClient aoeeGraphClient;
    private final GlobalIdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheService cacheService;
    private final EntityEventService entityEventService;

    public ReactionService(ReactionRepository reactionRepository,
                           FollowRepository followRepository,
                           UserService userService,
                           AoeeGraphClient aoeeGraphClient,
                           GlobalIdGenerator idGenerator,
                           ApplicationEventPublisher eventPublisher,
                           CacheService cacheService,
                           EntityEventService entityEventService) {
        this.reactionRepository = reactionRepository;
        this.followRepository = followRepository;
        this.userService = userService;
        this.aoeeGraphClient = aoeeGraphClient;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
        this.entityEventService = entityEventService;
    }

    @Transactional
    public ReactionEntity react(long userId, long targetId, String targetType, String reactionType) {
        // Remove existing reaction if present, flush to avoid unique constraint violation
        reactionRepository.findByTargetIdAndUserId(targetId, userId)
                .ifPresent(existing -> {
                    reactionRepository.delete(existing);
                    reactionRepository.flush();
                });

        var entity = new ReactionEntity();
        entity.setId(idGenerator.next(ObjectType.REACTION).value());
        entity.setUserId(userId);
        entity.setTargetId(targetId);
        entity.setTargetType(targetType);
        entity.setReactionType(reactionType);
        ReactionEntity saved = reactionRepository.save(entity);

        eventPublisher.publishEvent(new ReactionEvent(userId, targetId, reactionType, true));
        cacheService.evict("reactions:counts:" + targetId);
        try {
            entityEventService.publishReactionEvent("CREATE", saved.getId(), saved.getUserId(),
                saved.getTargetId(), saved.getTargetType(), saved.getReactionType(), saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        return saved;
    }

    @Transactional
    public void unreact(long userId, long targetId) {
        reactionRepository.findByTargetIdAndUserId(targetId, userId).ifPresent(existing -> {
            reactionRepository.delete(existing);
            eventPublisher.publishEvent(new ReactionEvent(userId, targetId, existing.getReactionType(), false));
            cacheService.evict("reactions:counts:" + targetId);
            try {
                entityEventService.publishReactionEvent("DELETE", existing.getId(), existing.getUserId(),
                    existing.getTargetId(), existing.getTargetType(), existing.getReactionType(), existing.getCreatedAt());
            } catch (Exception e) { /* don't affect main flow */ }
        });
    }

    /**
     * Get reactors for a target, prioritizing people the current user follows.
     * Shows "friends who liked" first, then others — like Facebook's
     * "Jane, Bob, and 72 others liked this".
     */
    public List<ReactorDto> getReactors(long targetId, long currentUserId) {
        // Get the set of user IDs that the current user follows
        Set<Long> followedUserIds = getFollowedUserIds(currentUserId);

        // First: find reactions from people the user follows (these go first)
        List<ReactionEntity> followedReactions = List.of();
        if (!followedUserIds.isEmpty()) {
            followedReactions = reactionRepository.findByTargetIdAndUserIdIn(
                    targetId, new ArrayList<>(followedUserIds));
        }

        // Second: get recent reactions from anyone (for the "others" section)
        List<ReactionEntity> recentReactions = reactionRepository.findTop20ByTargetIdOrderByCreatedAtDesc(targetId);

        // Combine: followed users first, then others (deduplicated)
        Set<Long> seenUserIds = new HashSet<>();
        List<ReactorDto> result = new ArrayList<>();

        // Add followed users' reactions first
        for (ReactionEntity reaction : followedReactions) {
            if (result.size() >= 20) break;
            if (seenUserIds.add(reaction.getUserId())) {
                userService.getById(reaction.getUserId()).ifPresent(user ->
                        result.add(new ReactorDto(
                                user.getId(),
                                user.getUsername(),
                                user.getDisplayName(),
                                user.getAvatarUrl(),
                                reaction.getReactionType(),
                                true
                        ))
                );
            }
        }

        // Fill remaining slots with other reactors
        for (ReactionEntity reaction : recentReactions) {
            if (result.size() >= 20) break;
            if (seenUserIds.add(reaction.getUserId())) {
                userService.getById(reaction.getUserId()).ifPresent(user ->
                        result.add(new ReactorDto(
                                user.getId(),
                                user.getUsername(),
                                user.getDisplayName(),
                                user.getAvatarUrl(),
                                reaction.getReactionType(),
                                false
                        ))
                );
            }
        }

        return result;
    }

    /** Backward-compatible version without current user context */
    public List<ReactorDto> getReactors(long targetId) {
        return getReactors(targetId, 0L);
    }

    public long getTotalCount(long targetId) {
        return reactionRepository.countByTargetId(targetId);
    }

    public Map<String, Long> getCounts(long targetId) {
        return cacheService.getWithType("reactions:counts:" + targetId,
                new TypeReference<Map<String, Long>>() {},
                Duration.ofSeconds(30), () -> {
            Map<String, Long> counts = new HashMap<>();
            for (Object[] row : reactionRepository.countGroupedByReactionType(targetId)) {
                counts.put((String) row[0], (Long) row[1]);
            }
            return counts;
        });
    }

    private Set<Long> getFollowedUserIds(long userId) {
        if (userId == 0L) return Set.of();

        // DB is the source of truth for follows
        Set<Long> dbFollowed = followRepository.findByFollowerId(userId).stream()
                .map(f -> f.getFollowedId())
                .collect(Collectors.toSet());

        // Merge with AOEE data (may have newer edges not yet in DB)
        try {
            List<Long> aoeeFollowed = aoeeGraphClient.getNeighbors(userId, "FOLLOWS");
            if (!aoeeFollowed.isEmpty()) {
                dbFollowed.addAll(aoeeFollowed);
            }
        } catch (Exception e) {
            // AOEE unavailable — DB data is sufficient
        }

        log.debug("User {} follows {} users", userId, dbFollowed.size());
        return dbFollowed;
    }
}
