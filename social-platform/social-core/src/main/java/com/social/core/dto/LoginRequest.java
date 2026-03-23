package com.social.core.dto;

public record LoginRequest(
        String username,
        String password
) {}
