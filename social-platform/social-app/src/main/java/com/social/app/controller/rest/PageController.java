package com.social.app.controller.rest;

import com.social.app.persistence.entity.PageEntity;
import com.social.app.service.PageService;
import com.social.app.service.PostService;
import com.social.app.persistence.repository.PostRepository;
import com.social.core.dto.CreatePageRequest;
import com.social.core.dto.MembershipDto;
import com.social.core.dto.PageDto;
import com.social.core.dto.PostDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageService pageService;
    private final PostService postService;
    private final PostRepository postRepository;

    public PageController(PageService pageService,
                          PostService postService,
                          PostRepository postRepository) {
        this.pageService = pageService;
        this.postService = postService;
        this.postRepository = postRepository;
    }

    @PostMapping
    public ResponseEntity<PageDto> createPage(@RequestBody CreatePageRequest request,
                                               Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        PageEntity entity = pageService.create(userId, request);
        return ResponseEntity.ok(pageService.toDto(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PageDto> updatePage(@PathVariable long id,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        PageEntity entity = pageService.update(userId, id,
                body.get("name"), body.get("description"),
                body.get("avatarUrl"), body.get("coverUrl"));
        return ResponseEntity.ok(pageService.toDto(entity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PageDto> getPage(@PathVariable long id) {
        return pageService.getById(id)
                .map(pageService::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<PageDto>> searchPages(@RequestParam("q") String query) {
        List<PageDto> pages = pageService.search(query).stream()
                .map(pageService::toDto)
                .toList();
        return ResponseEntity.ok(pages);
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<Void> followPage(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        pageService.follow(userId, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/unfollow")
    public ResponseEntity<Void> unfollowPage(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        pageService.unfollow(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members/{userId}/approve")
    public ResponseEntity<Void> approveMember(@PathVariable long id,
                                               @PathVariable long userId,
                                               Authentication auth) {
        long ownerId = (Long) auth.getPrincipal();
        pageService.approveMember(ownerId, id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<Boolean> isFollowing(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pageService.isFollowing(userId, id));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<MembershipDto>> getMembers(@PathVariable long id) {
        return ResponseEntity.ok(pageService.getMembers(id));
    }

    @PostMapping("/{id}/pin/{postId}")
    public ResponseEntity<Void> pinPost(@PathVariable long id, @PathVariable long postId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        pageService.pinPost(userId, id, postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/pin")
    public ResponseEntity<Void> unpinPost(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        pageService.unpinPost(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/posts")
    public ResponseEntity<List<PostDto>> getPagePosts(@PathVariable long id, Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        List<PostDto> posts = postRepository.findByTargetIdOrderByCreatedAtDesc(id).stream()
                .map(p -> postService.toDto(p, userId))
                .toList();
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<PageDto>> getMyPages(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        List<PageDto> pages = pageService.getUserPages(userId).stream()
                .map(pageService::toDto)
                .toList();
        return ResponseEntity.ok(pages);
    }
}
