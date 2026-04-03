package com.aisupport.webhook;

import com.aisupport.qa.AgenticQAService;
import com.aisupport.qa.QAService;
import com.aisupport.service.KnowledgeService;
import com.aisupport.service.SocialAppClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "\\?$|^(how|what|why|when|where|which|can|does|is|are|will|should|could|would|hi i|hello|hey)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private final QAService qaService;
    private final AgenticQAService agenticQaService;
    private final KnowledgeService knowledgeService;
    private final SocialAppClient socialClient;
    private final CapturedSolutionHandler solutionHandler;
    private final ObjectMapper mapper;

    public WebhookController(QAService qaService, AgenticQAService agenticQaService,
                              KnowledgeService knowledgeService,
                              SocialAppClient socialClient, CapturedSolutionHandler solutionHandler,
                              ObjectMapper mapper) {
        this.qaService = qaService;
        this.agenticQaService = agenticQaService;
        this.knowledgeService = knowledgeService;
        this.socialClient = socialClient;
        this.solutionHandler = solutionHandler;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receiveWebhook(
            @RequestHeader(value = "X-WorkSphere-Event", required = false) String eventType,
            @RequestHeader(value = "X-WorkSphere-Event-Id", required = false) String eventId,
            @RequestBody String rawBody) {

        log.info("Webhook received: event={}, eventId={}", eventType, eventId);

        try {
            JsonNode payload = mapper.readTree(rawBody);
            // Process in background thread — return 200 immediately so social app doesn't timeout
            java.util.concurrent.CompletableFuture.runAsync(() -> processEvent(payload));
        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage(), e);
        }

        // Return 200 immediately
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    private void processEvent(JsonNode payload) {
        try {
            String event = payload.path("event").asText();
            JsonNode installation = payload.path("installation");
            JsonNode data = payload.path("data");

            long targetId = installation.path("targetId").asLong();
            String installType = installation.path("type").asText();

            log.info("Processing event: {} for target {} (type={})", event, targetId, installType);

            // Find the knowledge set for this page/group
            var knowledgeSet = knowledgeService.getBySocialPageId(targetId).orElse(null);
            if (knowledgeSet == null) {
                log.warn("No knowledge set for target {} (type={}), skipping", targetId, installType);
                return;
            }

            log.info("Found knowledge set: {} (id={})", knowledgeSet.getName(), knowledgeSet.getId());

            switch (event) {
                case "POST_CREATED" -> handlePost(knowledgeSet.getId(), data.path("post"));
                case "COMMENT_CREATED" -> handleComment(knowledgeSet.getId(), data.path("comment"));
                default -> log.info("Unhandled event type: {}", event);
            }
        } catch (Exception e) {
            log.error("Error processing webhook event: {}", e.getMessage(), e);
        }
    }

    private void handlePost(long ksId, JsonNode post) {
        String content = post.path("content").asText("");
        // Support both formats: {id:...} and {postId:...}
        long postId = post.has("postId") ? post.path("postId").asLong() : post.path("id").asLong();
        long authorId = post.has("authorId") ? post.path("authorId").asLong() :
                post.path("author").path("id").asLong();
        log.info("handlePost: postId={}, authorId={}, content={}...", postId, authorId, content.substring(0, Math.min(50, content.length())));

        // Check if it looks like a question
        if (!isQuestion(content)) {
            // Check if it looks like a resolution
            solutionHandler.checkForSolution(ksId, content, postId, authorId,
                    post.path("author").path("displayName").asText(""));
            return;
        }

        log.info("Question detected on ks-{}: {}", ksId, content.substring(0, Math.min(100, content.length())));

        // Post immediate placeholder
        socialClient.postComment(postId, "\uD83E\uDD16 *Searching our knowledge base for an answer... please hold on.*");

        // Answer the question using the agentic pipeline
        var result = agenticQaService.answer(ksId, content, postId, authorId);

        // Post the answer back as a comment
        socialClient.postComment(postId, result.answer());

        // If escalation needed, create a support case
        if (result.suggestHuman()) {
            socialClient.createSupportCase(
                    "Auto-escalated question",
                    "Question: " + content + "\n\nAI answer (low confidence): " + result.answer(),
                    postId, null
            );
        }
    }

    private void handleComment(long ksId, JsonNode comment) {
        String content = comment.path("content").asText("");
        long postId = comment.has("postId") ? comment.path("postId").asLong() : comment.path("post_id").asLong();
        long commentId = comment.has("commentId") ? comment.path("commentId").asLong() : comment.path("id").asLong();
        long authorId = comment.path("author").path("id").asLong();

        // Don't respond to our own comments (prevent loops)
        if (socialClient.isBotUser(authorId)) return;

        // Check if it's a question
        if (isQuestion(content)) {
            socialClient.postComment(postId, "\uD83E\uDD16 *Looking into this... one moment.*");
            var result = agenticQaService.answer(ksId, content, postId, authorId);
            socialClient.postComment(postId, result.answer());

            if (result.suggestHuman()) {
                socialClient.createSupportCase(
                        "Follow-up question escalated",
                        "Question: " + content,
                        postId, commentId
                );
            }
        } else {
            // Check for resolution patterns
            solutionHandler.checkForSolution(ksId, content, postId, authorId,
                    comment.path("author").path("displayName").asText(""));
        }
    }

    private boolean isQuestion(String text) {
        if (text == null || text.isBlank()) return false;
        return QUESTION_PATTERN.matcher(text.trim()).find();
    }
}
