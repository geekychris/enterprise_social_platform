package com.social.app.controller.rest;

import com.social.app.service.CommentService;
import com.social.app.service.PostService;
import com.social.core.dto.CommentDto;
import com.social.core.dto.CreatePostRequest;
import com.social.core.dto.PostDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final CommentService commentService;

    public PostController(PostService postService, CommentService commentService) {
        this.postService = postService;
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<PostDto> createPost(@RequestBody CreatePostRequest request,
                                              Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var entity = postService.create(userId, request);
        return ResponseEntity.ok(postService.toDto(entity, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDto> getPost(@PathVariable long id, Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return postService.getById(id)
                .map(p -> postService.toDto(p, userId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CommentDto>> getComments(@PathVariable long id) {
        List<CommentDto> comments = commentService.getForPost(id).stream()
                .map(commentService::toDto)
                .toList();
        return ResponseEntity.ok(comments);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostDto> updatePost(@PathVariable long id,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return postService.getById(id)
                .filter(p -> p.getAuthorId().equals(userId))
                .map(p -> {
                    postService.update(p, body.get("content"));
                    return ResponseEntity.ok(postService.toDto(p, userId));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
