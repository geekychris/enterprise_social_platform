package com.social.core.dto;

import java.time.Instant;
import java.util.List;

public record ConversationDto(
        long id,
        String name,
        String type,
        List<UserSummaryDto> participants,
        MessageDto lastMessage,
        long unreadCount,
        Instant createdAt
) {}
