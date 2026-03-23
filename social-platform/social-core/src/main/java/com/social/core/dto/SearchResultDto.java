package com.social.core.dto;

import java.util.List;

public record SearchResultDto(
        List<SearchHit> hits,
        long totalHits
) {
    public record SearchHit(
            long id,
            String objectType,
            String name,
            String description,
            String avatarUrl,
            float score
    ) {}
}
