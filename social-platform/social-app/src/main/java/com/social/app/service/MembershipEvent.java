package com.social.app.service;

/**
 * Published when a user joins or leaves a group/team.
 */
public record MembershipEvent(long userId, long groupId, String role, boolean joined) {}
