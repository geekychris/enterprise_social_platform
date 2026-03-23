package com.social.app.controller.rest;

import com.social.app.persistence.entity.GroupEntity;
import com.social.app.service.GroupService;
import com.social.app.service.PostService;
import com.social.app.persistence.repository.PostRepository;
import com.social.core.dto.CreateGroupRequest;
import com.social.core.dto.GroupDto;
import com.social.core.dto.MembershipDto;
import com.social.core.dto.PostDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final PostService postService;
    private final PostRepository postRepository;

    public GroupController(GroupService groupService,
                           PostService postService,
                           PostRepository postRepository) {
        this.groupService = groupService;
        this.postService = postService;
        this.postRepository = postRepository;
    }

    @PostMapping
    public ResponseEntity<GroupDto> createGroup(@RequestBody CreateGroupRequest request,
                                                 Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        GroupEntity entity = groupService.create(userId, request);
        return ResponseEntity.ok(groupService.toDto(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupDto> updateGroup(@PathVariable long id,
                                                 @RequestBody Map<String, String> body,
                                                 Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        GroupEntity entity = groupService.update(userId, id,
                body.get("name"), body.get("description"),
                body.get("avatarUrl"), body.get("coverUrl"));
        return ResponseEntity.ok(groupService.toDto(entity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDto> getGroup(@PathVariable long id) {
        return groupService.getById(id)
                .map(groupService::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<GroupDto>> searchGroups(@RequestParam("q") String query) {
        List<GroupDto> groups = groupService.search(query).stream()
                .map(groupService::toDto)
                .toList();
        return ResponseEntity.ok(groups);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<MembershipDto> joinGroup(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        groupService.join(userId, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        groupService.leave(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members/{userId}/approve")
    public ResponseEntity<Void> approveMember(@PathVariable long id,
                                               @PathVariable long userId,
                                               Authentication auth) {
        long ownerId = (Long) auth.getPrincipal();
        groupService.approveMember(ownerId, id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/members/{userId}/reject")
    public ResponseEntity<Void> rejectMember(@PathVariable long id,
                                              @PathVariable long userId,
                                              Authentication auth) {
        long ownerId = (Long) auth.getPrincipal();
        groupService.rejectMember(ownerId, id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/membership")
    public ResponseEntity<MembershipDto> getMyMembership(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return groupService.getMembership(userId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<MembershipDto>> getMembers(@PathVariable long id) {
        return ResponseEntity.ok(groupService.getMembers(id));
    }

    @GetMapping("/{id}/pending")
    public ResponseEntity<List<MembershipDto>> getPendingMembers(@PathVariable long id,
                                                                   Authentication auth) {
        // Only owner/admin should see pending members - validation happens in service
        return ResponseEntity.ok(groupService.getPendingMembers(id));
    }

    @PostMapping("/{id}/pin/{postId}")
    public ResponseEntity<Void> pinPost(@PathVariable long id, @PathVariable long postId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        groupService.pinPost(userId, id, postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/pin")
    public ResponseEntity<Void> unpinPost(@PathVariable long id, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        groupService.unpinPost(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/posts")
    public ResponseEntity<List<PostDto>> getGroupPosts(@PathVariable long id, Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        List<PostDto> posts = postRepository.findByTargetIdOrderByCreatedAtDesc(id).stream()
                .map(p -> postService.toDto(p, userId))
                .toList();
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<GroupDto>> getMyGroups(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        List<GroupDto> groups = groupService.getUserGroups(userId).stream()
                .map(groupService::toDto)
                .toList();
        return ResponseEntity.ok(groups);
    }
}
