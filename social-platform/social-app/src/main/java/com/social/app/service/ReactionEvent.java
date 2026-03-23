package com.social.app.service;

/**
 * Published when a user reacts or unreacts to a target.
 */
public record ReactionEvent(long userId, long targetId, String reactionType, boolean added) {}
