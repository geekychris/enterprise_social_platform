package com.social.core.dto;

import java.time.Instant;
import java.util.List;

public record MessageDto(
        long id,
        long conversationId,
        UserSummaryDto sender,
        String content,
        List<AttachmentDto> attachments,
        boolean read,
        Instant createdAt
) {}
