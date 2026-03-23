package com.social.app.controller.rest;

import com.social.app.service.CommentService;
import com.social.core.dto.CommentDto;
import com.social.core.dto.CreateCommentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<CommentDto> createComment(@RequestBody CreateCommentRequest request,
                                                    Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var entity = commentService.create(userId, request);
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
}
