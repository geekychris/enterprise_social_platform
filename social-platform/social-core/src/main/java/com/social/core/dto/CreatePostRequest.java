package com.social.core.dto;

import com.social.core.model.TargetType;
import com.social.core.model.Visibility;

import java.util.List;

public record CreatePostRequest(
        String content,
        TargetType targetType,
        Long targetId,
        Visibility visibility,
        List<Long> attachmentIds
) {
    /** Convenience constructor for callers that don't supply attachment IDs. */
    public CreatePostRequest(String content, TargetType targetType, Long targetId, Visibility visibility) {
        this(content, targetType, targetId, visibility, null);
    }
}
