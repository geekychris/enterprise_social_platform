package com.social.core.dto;

public record UserSummaryDto(
        long id,
        String username,
        String displayName,
        String avatarUrl
) {}
