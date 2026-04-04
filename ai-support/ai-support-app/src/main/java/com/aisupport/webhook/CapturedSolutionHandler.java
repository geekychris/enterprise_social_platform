package com.aisupport.webhook;

import com.aisupport.persistence.entity.CapturedSolutionEntity;
import com.aisupport.persistence.repository.CapturedSolutionRepository;
import com.aisupport.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CapturedSolutionHandler {
    private static final Logger log = LoggerFactory.getLogger(CapturedSolutionHandler.class);
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile(
            "(i solved|i fixed|solution was|the fix is|figured it out|resolved by|" +
            "worked for me|this worked|the answer is|i found that|turns out|" +
            "problem was|issue was|root cause|workaround is)",
            Pattern.CASE_INSENSITIVE
    );

    // IDs of known support/admin users whose answers are auto-promoted
    private static final Set<Long> TRUSTED_USER_IDS = Set.of(
            72057594037927937L, // Lamar (admin)
            72057594037927938L, // Joshua (admin)
            72057594037927939L  // Cecilia (admin)
    );

    private final CapturedSolutionRepository capturedSolutionRepository;
    private final KnowledgeService knowledgeService;

    public CapturedSolutionHandler(CapturedSolutionRepository capturedSolutionRepository,
                                    KnowledgeService knowledgeService) {
        this.capturedSolutionRepository = capturedSolutionRepository;
        this.knowledgeService = knowledgeService;
    }

    @Transactional
    public void checkForSolution(long knowledgeSetId, String content, long postId,
                                  long authorId, String authorName) {
        if (content == null || content.length() < 20) return;

        if (RESOLUTION_PATTERN.matcher(content).find()) {
            boolean isTrusted = TRUSTED_USER_IDS.contains(authorId);
            String sourceType = isTrusted ? "SUPPORT" : "USER";

            log.info("Potential solution detected from {} ({}) in ks-{}", authorName, sourceType, knowledgeSetId);

            var solution = new CapturedSolutionEntity();
            solution.setKnowledgeSetId(knowledgeSetId);
            solution.setQuestion("(from post " + postId + ")");
            solution.setSolution(content);
            solution.setSourceUserId(authorId);
            solution.setSourceUsername(authorName);
            solution.setSourceType(sourceType);

            // All solutions go to PENDING for review — trusted users are tagged for easy identification
            solution.setStatus("PENDING");
            solution.setReviewerNotes(isTrusted ? "From trusted support user — recommended for promotion" : null);
            capturedSolutionRepository.save(solution);
            log.info("Captured solution from {} ({}) as PENDING in ks-{}", authorName, sourceType, knowledgeSetId);
        }
    }

    /**
     * Capture a helpful answer from another user (not "I solved it" but "you should try X").
     * These are always queued for review since they come from community members
     * answering other people's questions.
     */
    @Transactional
    public void checkForAnswer(long knowledgeSetId, String content, long postId,
                                long authorId, String authorName) {
        if (content == null || content.length() < 30) return;

        boolean isTrusted = TRUSTED_USER_IDS.contains(authorId);
        String sourceType = isTrusted ? "SUPPORT" : "COMMUNITY_ANSWER";

        log.info("Captured answer from {} ({}) in ks-{}", authorName, sourceType, knowledgeSetId);

        var solution = new CapturedSolutionEntity();
        solution.setKnowledgeSetId(knowledgeSetId);
        solution.setQuestion("(answer on post " + postId + ")");
        solution.setSolution(content);
        solution.setSourceUserId(authorId);
        solution.setSourceUsername(authorName);
        solution.setSourceType(sourceType);

        // All answers go to PENDING for review
        solution.setStatus("PENDING");
        solution.setReviewerNotes(isTrusted ? "From trusted support user — recommended for promotion" : null);
        capturedSolutionRepository.save(solution);
        log.info("Captured answer from {} ({}) as PENDING in ks-{}", authorName, sourceType, knowledgeSetId);
    }
}
