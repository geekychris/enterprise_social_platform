package com.social.core.dto;

import com.social.core.model.Visibility;

public record PageDto(
        long id,
        String name,
        String slug,
        String description,
        String avatarUrl,
        String coverUrl,
        Visibility visibility,
        String ownerType,
        long ownerId,
        long followerCount,
        Long pinnedPostId
) {}
