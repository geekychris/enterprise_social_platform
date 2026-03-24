package com.social.core.dto;

public record UserDto(
        long id,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        String coverUrl,
        String bio,
        String visibility,
        long followerCount,
        long followingCount,
        boolean admin,
        String phone,
        String location,
        String jobTitle,
        String department,
        String joinedCompanyAt,
        Long managerId,
        String managerName,
        String interests,
        String skills,
        String linkedinUrl,
        String timezone,
        String pronouns
) {}
