package com.social.core.dto;

import com.social.core.model.Visibility;

public record GroupDto(
        long id,
        String name,
        String slug,
        String description,
        String avatarUrl,
        String coverUrl,
        Visibility visibility,
        long memberCount,
        Long pinnedPostId
) {}
