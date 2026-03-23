package com.social.core.dto;

import com.social.core.model.Visibility;

public record UserDto(
        long id,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        String bio,
        Visibility visibility,
        long followerCount,
        long followingCount,
        boolean admin
) {}
