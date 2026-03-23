package com.social.core.dto;

import java.time.Instant;

public record MembershipDto(
        long userId,
        long groupId,
        String role,
        String status,
        String userName,
        String userAvatarUrl,
        Instant joinedAt
) {}
