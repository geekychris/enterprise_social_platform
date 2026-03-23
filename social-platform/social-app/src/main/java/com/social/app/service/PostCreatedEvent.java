package com.social.app.service;

/**
 * Published when a new post is created.
 */
public record PostCreatedEvent(long postId, long authorId, String targetType, Long targetId) {}
