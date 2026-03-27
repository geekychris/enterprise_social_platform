package com.social.core.dto;

import java.time.Instant;
import java.util.List;

public record PollDto(
    long id, String question, boolean allowMultiple, Instant closesAt, boolean closed,
    List<PollOptionDto> options, int totalVotes, List<Long> currentUserVotes
) {}
