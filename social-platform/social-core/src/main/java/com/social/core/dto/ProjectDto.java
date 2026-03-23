package com.social.core.dto;

import com.social.core.model.Visibility;

public record ProjectDto(
        long id,
        String name,
        String slug,
        String description,
        Visibility visibility,
        Long pageId
) {}
