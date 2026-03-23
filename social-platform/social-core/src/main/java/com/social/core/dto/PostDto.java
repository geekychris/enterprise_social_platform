package com.social.core.dto;

import com.social.core.model.TargetType;
import com.social.core.model.Visibility;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PostDto(
        long id,
        UserSummaryDto author,
        String content,
        TargetType targetType,
        long targetId,
        Visibility visibility,
        List<AttachmentDto> attachments,
        Map<String, Long> reactionCounts,
        String currentUserReaction,
        long commentCount,
        Instant createdAt,
        boolean recommended,
        Double recommendationScore
) {
    /** Convenience constructor for organic (non-recommended) posts. */
    public PostDto(long id, UserSummaryDto author, String content, TargetType targetType,
                   long targetId, Visibility visibility, List<AttachmentDto> attachments,
                   Map<String, Long> reactionCounts, String currentUserReaction,
                   long commentCount, Instant createdAt) {
        this(id, author, content, targetType, targetId, visibility, attachments,
             reactionCounts, currentUserReaction, commentCount, createdAt, false, null);
    }

    public PostDto asRecommended(double score) {
        return new PostDto(id, author, content, targetType, targetId, visibility,
                attachments, reactionCounts, currentUserReaction, commentCount,
                createdAt, true, score);
    }
}
