package com.social.core.dto;

public record AuthResponse(
        String token,
        long userId,
        String username,
        boolean admin
) {}
