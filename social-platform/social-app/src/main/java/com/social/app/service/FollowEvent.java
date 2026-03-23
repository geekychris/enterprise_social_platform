package com.social.app.service;

/**
 * Published when a user follows or unfollows another user.
 */
public record FollowEvent(long followerId, long followedId, boolean followed) {}
