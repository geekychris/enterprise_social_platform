package com.social.app.controller.rest;

import com.social.app.persistence.entity.FollowEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.service.UserService;
import com.social.core.dto.UserDto;
import com.social.core.dto.UserSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FollowRepository followRepository;

    public UserController(UserService userService, FollowRepository followRepository) {
        this.userService = userService;
        this.followRepository = followRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable long id) {
        return userService.getById(id)
                .map(userService::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSummaryDto>> searchUsers(@RequestParam("q") String query) {
        List<UserSummaryDto> results = userService.search(query).stream()
                .map(userService::toSummaryDto)
                .toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<List<UserSummaryDto>> getFollowers(@PathVariable long id) {
        List<UserSummaryDto> followers = followRepository.findByFollowedId(id).stream()
                .map(FollowEntity::getFollowerId)
                .map(followerId -> userService.getById(followerId).orElse(null))
                .filter(u -> u != null)
                .map(userService::toSummaryDto)
                .toList();
        return ResponseEntity.ok(followers);
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<List<UserSummaryDto>> getFollowing(@PathVariable long id) {
        List<UserSummaryDto> following = followRepository.findByFollowerId(id).stream()
                .map(FollowEntity::getFollowedId)
                .map(followedId -> userService.getById(followedId).orElse(null))
                .filter(u -> u != null)
                .map(userService::toSummaryDto)
                .toList();
        return ResponseEntity.ok(following);
    }
}
