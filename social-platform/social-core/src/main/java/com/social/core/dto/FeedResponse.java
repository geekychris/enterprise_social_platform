package com.social.core.dto;

import java.util.List;

public record FeedResponse(
        List<PostDto> posts,
        String nextCursor,
        boolean hasMore
) {}
