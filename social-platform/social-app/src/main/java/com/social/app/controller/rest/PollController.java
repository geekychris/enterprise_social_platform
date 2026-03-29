package com.social.app.controller.rest;

import com.social.app.persistence.repository.PollRepository;
import com.social.app.service.AnalyticsService;
import com.social.app.service.PollService;
import com.social.core.dto.PollDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    private static final Logger log = LoggerFactory.getLogger(PollController.class);

    private final PollService pollService;
    private final PollRepository pollRepository;
    private final AnalyticsService analyticsService;

    public PollController(PollService pollService, PollRepository pollRepository,
                          AnalyticsService analyticsService) {
        this.pollService = pollService;
        this.pollRepository = pollRepository;
        this.analyticsService = analyticsService;
    }

    @PostMapping
    public ResponseEntity<PollDto> createPoll(@RequestBody CreatePollRequest request,
                                              Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        Instant closesAt = request.closesAt() != null ? request.closesAt() : null;
        var poll = pollService.createPoll(
                userId, request.postId(), request.question(),
                List.of(request.options()), request.allowMultiple(), closesAt);
        return ResponseEntity.ok(pollService.toDto(poll.getId(), userId));
    }

    @GetMapping("/{pollId}")
    public ResponseEntity<PollDto> getPoll(@PathVariable long pollId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pollService.toDto(pollId, userId));
    }

    @PostMapping("/{pollId}/vote")
    public ResponseEntity<PollDto> vote(@PathVariable long pollId,
                                        @RequestBody VoteRequest request,
                                        Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        pollService.vote(userId, pollId, List.of(request.optionIds()));

        // Log analytics (fire-and-forget)
        try {
            long postId = pollRepository.findById(pollId)
                    .map(p -> p.getPostId())
                    .orElse(0L);
            analyticsService.logPollVote(userId, pollId, postId);
        } catch (Exception e) {
            log.debug("Failed to log poll vote analytics: {}", e.getMessage());
        }

        return ResponseEntity.ok(pollService.toDto(pollId, userId));
    }

    @DeleteMapping("/{pollId}/vote")
    public ResponseEntity<Void> removeVote(@PathVariable long pollId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        pollService.removeVote(userId, pollId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<PollDto> getPollByPost(@PathVariable long postId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return pollRepository.findByPostId(postId)
                .map(poll -> ResponseEntity.ok(pollService.toDto(poll.getId(), userId)))
                .orElse(ResponseEntity.notFound().build());
    }

    public record CreatePollRequest(long postId, String question, String[] options,
                                    boolean allowMultiple, Instant closesAt) {}

    public record VoteRequest(Long[] optionIds) {}
}
