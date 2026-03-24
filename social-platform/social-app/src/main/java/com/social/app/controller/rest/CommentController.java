package com.social.app.controller.rest;

import com.social.app.service.CommentService;
import com.social.app.service.NotificationService;
import com.social.app.service.PostService;
import com.social.core.dto.CommentDto;
import com.social.core.dto.CreateCommentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;
    private final NotificationService notificationService;
    private final PostService postService;

    public CommentController(CommentService commentService,
                             NotificationService notificationService,
                             PostService postService) {
        this.commentService = commentService;
        this.notificationService = notificationService;
        this.postService = postService;
    }

    @PostMapping
    public ResponseEntity<CommentDto> createComment(@RequestBody Map<String, Object> body,
                                                    Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        long postId = parseLong(body.get("postId"));
        Long parentCommentId = body.get("parentCommentId") != null ? parseLong(body.get("parentCommentId")) : null;
        String content = (String) body.get("content");

        List<Long> attachmentIds = List.of();
        if (body.containsKey("attachmentIds") && body.get("attachmentIds") instanceof List<?> rawList) {
            attachmentIds = rawList.stream()
                    .map(CommentController::parseLong)
                    .toList();
        }

        var request = new CreateCommentRequest(postId, parentCommentId, content, attachmentIds);
        var entity = commentService.create(userId, request);

        // Notifications: mention + comment on post (use postId so notification links to the post)
        notificationService.processMentions(content, userId, postId, "POST");

        // Notify post author about the comment
        var post = postService.getById(postId);
        if (post.isPresent() && post.get().getAuthorId() != userId) {
            notificationService.notifyComment(post.get().getAuthorId(), userId, postId);
        }

        return ResponseEntity.ok(commentService.toDto(entity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommentDto> getComment(@PathVariable long id) {
        return commentService.getById(id)
                .map(commentService::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommentDto> updateComment(@PathVariable long id,
                                                      @RequestBody Map<String, String> body,
                                                      Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return commentService.getById(id)
                .filter(c -> c.getAuthorId().equals(userId))
                .map(c -> {
                    commentService.update(c, body.get("content"));
                    return ResponseEntity.ok(commentService.toDto(c));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static long parseLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot parse as long: " + value);
    }
}
