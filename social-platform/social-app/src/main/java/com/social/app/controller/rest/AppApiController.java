package com.social.app.controller.rest;

import com.social.app.persistence.entity.AppEventEntity;
import com.social.app.service.AppService;
import com.social.app.service.CommentService;
import com.social.app.service.MessageService;
import com.social.app.service.PostService;
import com.social.core.dto.CreateCommentRequest;
import com.social.core.dto.CreatePostRequest;
import com.social.core.model.TargetType;
import com.social.core.model.Visibility;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.social.app.persistence.entity.SupportCaseEntity;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apps")
public class AppApiController {

    private final AppService appService;
    private final CommentService commentService;
    private final PostService postService;
    private final MessageService messageService;
    private final com.social.app.persistence.repository.SupportCaseRepository caseRepository;
    private final com.social.core.id.GlobalIdGenerator idGenerator;

    public AppApiController(AppService appService,
                            CommentService commentService,
                            PostService postService,
                            MessageService messageService,
                            com.social.app.persistence.repository.SupportCaseRepository caseRepository,
                            com.social.core.id.GlobalIdGenerator idGenerator) {
        this.appService = appService;
        this.commentService = commentService;
        this.postService = postService;
        this.caseRepository = caseRepository;
        this.idGenerator = idGenerator;
        this.messageService = messageService;
    }

    private boolean authenticate(String appIdHeader, String auth) {
        try {
            long appId = Long.parseLong(appIdHeader);
            String apiKey = auth.replace("Bearer ", "");
            return appService.validateApiKey(appId, apiKey);
        } catch (Exception e) {
            return false;
        }
    }

    @PostMapping("/comments")
    public ResponseEntity<?> createComment(@RequestBody Map<String, Object> body,
                                            @RequestHeader("Authorization") String auth,
                                            @RequestHeader("X-App-Id") String appId) {
        if (!authenticate(appId, auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        Object postIdVal = body.get("postId");
        if (postIdVal == null) return ResponseEntity.badRequest().body(Map.of("error", "postId is required"));
        long postId = postIdVal instanceof Number ? ((Number) postIdVal).longValue() : Long.parseLong(postIdVal.toString());
        String content = (String) body.get("content");
        if (content == null) return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        Long parentCommentId = body.get("parentCommentId") != null ? ((Number) body.get("parentCommentId")).longValue() : null;

        // Use the app's creator as the author (or the existing bot user 72057594037999999)
        long authorId = appService.getById(Long.parseLong(appId))
                .map(app -> app.getCreatedBy() != null ? app.getCreatedBy() : 72057594037999999L)
                .orElse(72057594037999999L);
        var request = new CreateCommentRequest(postId, parentCommentId, content, null);
        var comment = commentService.create(authorId, request);

        return ResponseEntity.ok(Map.of(
                "id", comment.getId(),
                "postId", comment.getPostId(),
                "content", comment.getContent(),
                "createdAt", comment.getCreatedAt().toString()
        ));
    }

    @PostMapping("/cards")
    public ResponseEntity<?> createCard(@RequestBody Map<String, Object> body,
                                         @RequestHeader("Authorization") String auth,
                                         @RequestHeader("X-App-Id") String appId) {
        if (!authenticate(appId, auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        long postId = ((Number) body.get("postId")).longValue();
        // Rich cards are comments with JSON content wrapped in a card marker
        String cardJson = "{\"type\":\"card\"," +
                "\"title\":" + quote(body.get("title")) + "," +
                "\"body\":" + quote(body.get("body")) + "," +
                "\"actions\":" + (body.get("actions") != null ? body.get("actions").toString() : "[]") +
                "}";

        long authorId = Long.parseLong(appId);
        var request = new CreateCommentRequest(postId, null, cardJson, null);
        var comment = commentService.create(authorId, request);

        return ResponseEntity.ok(Map.of(
                "id", comment.getId(),
                "postId", comment.getPostId(),
                "createdAt", comment.getCreatedAt().toString()
        ));
    }

    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> body,
                                          @RequestHeader("Authorization") String auth,
                                          @RequestHeader("X-App-Id") String appId) {
        if (!authenticate(appId, auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        long conversationId = ((Number) body.get("conversationId")).longValue();
        String content = (String) body.get("content");
        long senderId = Long.parseLong(appId);

        var message = messageService.send(senderId, conversationId, content, null);

        return ResponseEntity.ok(Map.of(
                "id", message.getId(),
                "conversationId", message.getConversationId(),
                "content", message.getContent(),
                "createdAt", message.getCreatedAt().toString()
        ));
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@RequestBody Map<String, Object> body,
                                         @RequestHeader("Authorization") String auth,
                                         @RequestHeader("X-App-Id") String appId) {
        if (!authenticate(appId, auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        String content = (String) body.get("content");
        String targetTypeStr = (String) body.get("targetType");
        Long targetId = body.get("targetId") != null ? ((Number) body.get("targetId")).longValue() : null;
        String visibilityStr = (String) body.get("visibility");

        TargetType targetType = targetTypeStr != null ? TargetType.valueOf(targetTypeStr) : null;
        Visibility visibility = visibilityStr != null ? Visibility.valueOf(visibilityStr) : Visibility.PUBLIC;

        long authorId = Long.parseLong(appId);
        var request = new CreatePostRequest(content, targetType, targetId, visibility, null);
        var post = postService.create(authorId, request);

        return ResponseEntity.ok(Map.of(
                "id", post.getId(),
                "content", post.getContent(),
                "createdAt", post.getCreatedAt().toString()
        ));
    }

    @GetMapping("/events")
    public ResponseEntity<?> getEvents(@RequestHeader("X-App-Id") String appIdHeader,
                                        @RequestHeader("Authorization") String auth,
                                        @RequestParam(defaultValue = "PENDING") String status) {
        if (!authenticate(appIdHeader, auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        long appId = Long.parseLong(appIdHeader);
        List<AppEventEntity> events = appService.getEvents(appId, status);

        List<Map<String, Object>> result = events.stream().map(e -> Map.<String, Object>of(
                "id", e.getId(),
                "eventType", e.getEventType(),
                "payload", e.getPayload(),
                "status", e.getStatus(),
                "createdAt", e.getCreatedAt().toString()
        )).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/events/{eventId}/ack")
    public ResponseEntity<?> ackEvent(@PathVariable long eventId,
                                       @RequestHeader("Authorization") String auth,
                                       @RequestHeader("X-App-Id") String appId) {
        if (!authenticate(appId, auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        appService.ackEvent(eventId);
        return ResponseEntity.ok(Map.of("status", "acknowledged"));
    }

    // ── Cases ─────────────────────────────────────────────────────

    @PostMapping("/cases")
    public ResponseEntity<?> createCase(@RequestBody Map<String, Object> body,
                                         @RequestHeader("Authorization") String auth,
                                         @RequestHeader("X-App-Id") String appId) {
        if (!authenticate(appId, auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        SupportCaseEntity caseEntity = new SupportCaseEntity();
        caseEntity.setId(idGenerator.next(com.social.core.id.ObjectType.SUPPORT_CASE).value());
        caseEntity.setTenantId(com.social.app.tenant.TenantContext.getTenantId());
        caseEntity.setAppId(Long.parseLong(appId));
        caseEntity.setTitle((String) body.get("title"));
        caseEntity.setDescription((String) body.get("description"));
        Object reqId = body.get("requesterId");
        caseEntity.setRequesterId(reqId instanceof Number ? ((Number) reqId).longValue() : Long.parseLong(reqId.toString()));
        caseEntity.setStatus("OPEN");
        caseEntity.setPriority((String) body.getOrDefault("priority", "NORMAL"));
        if (body.get("sourcePostId") != null) {
            Object spId = body.get("sourcePostId");
            caseEntity.setSourcePostId(spId instanceof Number ? ((Number) spId).longValue() : Long.parseLong(spId.toString()));
        }

        // Generate case number
        String caseNumber = "CS-" + String.format("%04d", System.currentTimeMillis() % 10000);
        caseEntity.setCaseNumber(caseNumber);

        caseEntity = caseRepository.save(caseEntity);

        return ResponseEntity.ok(Map.of(
                "id", caseEntity.getId(),
                "caseNumber", caseEntity.getCaseNumber(),
                "status", caseEntity.getStatus(),
                "title", caseEntity.getTitle()
        ));
    }

    private String quote(Object value) {
        if (value == null) return "null";
        return "\"" + value.toString().replace("\"", "\\\"") + "\"";
    }
}
