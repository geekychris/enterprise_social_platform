package com.social.core.dto;

import com.social.core.model.Visibility;

public record TeamDto(
        long id,
        String name,
        String slug,
        String description,
        Visibility visibility,
        long memberCount
) {}
