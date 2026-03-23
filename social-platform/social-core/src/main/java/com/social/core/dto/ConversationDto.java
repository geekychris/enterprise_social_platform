package com.social.core.dto;

public record ConversationDto(
        UserSummaryDto partner,
        MessageDto lastMessage,
        long unreadCount
) {}
