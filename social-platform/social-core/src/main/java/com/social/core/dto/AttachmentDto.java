package com.social.core.dto;

public record AttachmentDto(
        long id,
        String fileUrl,
        String mediaType,
        Long fileSize,
        Integer width,
        Integer height
) {}
