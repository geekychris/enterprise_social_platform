package com.social.core.dto;

import java.util.List;

public record CreateCommentRequest(
        long postId,
        Long parentCommentId,
        String content,
        List<Long> attachmentIds
) {}
