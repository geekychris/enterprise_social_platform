package com.social.core.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CommentDto(
        long id,
        UserSummaryDto author,
        String content,
        int depth,
        long postId,
        Long parentCommentId,
        List<AttachmentDto> attachments,
        Map<String, Long> reactionCounts,
        String currentUserReaction,
        List<CommentDto> replies,
        Instant createdAt
) {}
