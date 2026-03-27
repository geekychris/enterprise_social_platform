package com.social.core.dto;

public record OrgUnitDto(
        long id,
        String name,
        String type,
        Long parentId,
        Long headUserId,
        String headUserName,
        String description,
        String costCenter,
        int childCount,
        int memberCount
) {}
