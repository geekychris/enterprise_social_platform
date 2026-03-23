package com.social.core.dto;

public record CreateUserRequest(
        String username,
        String displayName,
        String email,
        String password,
        String bio
) {}
