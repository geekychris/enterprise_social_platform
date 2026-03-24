package com.social.app.controller.rest;

import com.social.app.persistence.entity.FollowEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.service.UserService;
import com.social.core.dto.UserDto;
import com.social.core.dto.UserSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

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
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam("q") String query) {
        List<UserDto> results = userService.search(query).stream()
                .map(userService::toDto)
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

    @PutMapping("/me/profile")
    public ResponseEntity<UserDto> updateProfile(@RequestBody Map<String, Object> body,
                                                  Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var user = userService.getById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (body.containsKey("displayName")) user.setDisplayName((String) body.get("displayName"));
        if (body.containsKey("bio")) user.setBio((String) body.get("bio"));
        if (body.containsKey("avatarUrl")) user.setAvatarUrl((String) body.get("avatarUrl"));
        if (body.containsKey("coverUrl")) user.setCoverUrl((String) body.get("coverUrl"));
        if (body.containsKey("phone")) user.setPhone((String) body.get("phone"));
        if (body.containsKey("location")) user.setLocation((String) body.get("location"));
        if (body.containsKey("jobTitle")) user.setJobTitle((String) body.get("jobTitle"));
        if (body.containsKey("department")) user.setDepartment((String) body.get("department"));
        if (body.containsKey("interests")) user.setInterests((String) body.get("interests"));
        if (body.containsKey("skills")) user.setSkills((String) body.get("skills"));
        if (body.containsKey("linkedinUrl")) user.setLinkedinUrl((String) body.get("linkedinUrl"));
        if (body.containsKey("timezone")) user.setTimezone((String) body.get("timezone"));
        if (body.containsKey("pronouns")) user.setPronouns((String) body.get("pronouns"));
        if (body.containsKey("joinedCompanyAt") && body.get("joinedCompanyAt") != null) {
            user.setJoinedCompanyAt(java.time.LocalDate.parse((String) body.get("joinedCompanyAt")));
        }
        if (body.containsKey("managerId")) {
            Object mgrId = body.get("managerId");
            user.setManagerId(mgrId != null ? (mgrId instanceof Number n ? n.longValue() : Long.parseLong(mgrId.toString())) : null);
        }

        userService.save(user);
        return ResponseEntity.ok(userService.toDto(user));
    }
}
