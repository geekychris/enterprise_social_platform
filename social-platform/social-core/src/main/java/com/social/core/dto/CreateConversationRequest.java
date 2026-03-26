package com.social.core.dto;

import java.util.List;

public record CreateConversationRequest(
        List<Long> participantIds,
        String name
) {}
