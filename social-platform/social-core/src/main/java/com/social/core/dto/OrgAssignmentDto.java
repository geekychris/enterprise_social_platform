package com.social.core.dto;

public record OrgAssignmentDto(
        long id,
        long userId,
        String userName,
        String userAvatarUrl,
        long orgUnitId,
        String orgUnitName,
        String title,
        String relationshipType,
        Long reportsToUserId,
        String reportsToUserName,
        String level,
        String startDate
) {}
