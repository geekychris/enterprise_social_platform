package com.social.core.dto;

public record ReactorDto(
        long userId,
        String username,
        String displayName,
        String avatarUrl,
        String reactionType,
        boolean followed
) {
    /** Backward-compatible constructor without followed flag */
    public ReactorDto(long userId, String username, String displayName, String avatarUrl, String reactionType) {
        this(userId, username, displayName, avatarUrl, reactionType, false);
    }
}
