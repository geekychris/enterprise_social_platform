package com.social.core.dto;

public record CreateCommentRequest(
        long postId,
        Long parentCommentId,
        String content
) {}
