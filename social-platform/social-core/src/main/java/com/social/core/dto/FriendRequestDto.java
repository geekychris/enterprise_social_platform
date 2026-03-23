package com.social.core.dto;

import java.time.Instant;

public record FriendRequestDto(
    long id,
    long senderId,
    String senderUsername,
    String senderDisplayName,
    String senderAvatarUrl,
    long receiverId,
    String receiverUsername,
    String receiverDisplayName,
    String receiverAvatarUrl,
    String status,
    Instant createdAt
) {}
