package com.social.app.controller.rest;

import com.social.app.persistence.entity.FriendRequestEntity;
import com.social.app.persistence.entity.FollowEntity;
import com.social.app.persistence.repository.FriendRequestRepository;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.service.FollowEvent;
import com.social.app.service.UserService;
import com.social.core.dto.FriendRequestDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friend-requests")
public class FriendRequestController {

    private final FriendRequestRepository friendRequestRepository;
    private final FollowRepository followRepository;
    private final UserService userService;
    private final GlobalIdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public FriendRequestController(FriendRequestRepository friendRequestRepository,
                                    FollowRepository followRepository,
                                    UserService userService,
                                    GlobalIdGenerator idGenerator,
                                    ApplicationEventPublisher eventPublisher) {
        this.friendRequestRepository = friendRequestRepository;
        this.followRepository = followRepository;
        this.userService = userService;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/{targetId}")
    public ResponseEntity<?> sendRequest(@PathVariable long targetId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        if (userId == targetId) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot send friend request to yourself"));
        }

        // Check if request already exists in either direction
        if (friendRequestRepository.existsBySenderIdAndReceiverId(userId, targetId) ||
            friendRequestRepository.existsBySenderIdAndReceiverId(targetId, userId)) {
            return ResponseEntity.ok(Map.of("status", "ALREADY_EXISTS"));
        }

        // Check if already following each other (already friends)
        if (followRepository.existsByFollowerIdAndFollowedId(userId, targetId) &&
            followRepository.existsByFollowerIdAndFollowedId(targetId, userId)) {
            return ResponseEntity.ok(Map.of("status", "ALREADY_FRIENDS"));
        }

        var entity = new FriendRequestEntity();
        entity.setId(idGenerator.next(ObjectType.USER).value());
        entity.setSenderId(userId);
        entity.setReceiverId(targetId);
        entity.setStatus("PENDING");
        friendRequestRepository.save(entity);

        return ResponseEntity.ok(Map.of("status", "SENT", "id", entity.getId()));
    }

    @GetMapping("/received")
    public ResponseEntity<List<FriendRequestDto>> getReceived(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var requests = friendRequestRepository.findByReceiverIdAndStatus(userId, "PENDING");
        return ResponseEntity.ok(toDtoList(requests));
    }

    @GetMapping("/sent")
    public ResponseEntity<List<FriendRequestDto>> getSent(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var requests = friendRequestRepository.findBySenderIdAndStatus(userId, "PENDING");
        return ResponseEntity.ok(toDtoList(requests));
    }

    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable long requestId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var request = friendRequestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getReceiverId() != userId) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your request"));
        }
        if (!"PENDING".equals(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request is not pending"));
        }

        request.setStatus("ACCEPTED");
        friendRequestRepository.save(request);

        // Create mutual follow relationships
        createFollowIfNotExists(request.getSenderId(), request.getReceiverId());
        createFollowIfNotExists(request.getReceiverId(), request.getSenderId());

        return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(@PathVariable long requestId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var request = friendRequestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getReceiverId() != userId) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your request"));
        }
        if (!"PENDING".equals(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request is not pending"));
        }

        request.setStatus("REJECTED");
        friendRequestRepository.save(request);

        return ResponseEntity.ok(Map.of("status", "REJECTED"));
    }

    @GetMapping("/status/{targetUserId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable long targetUserId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();

        // Check if already mutual friends (both follow each other)
        boolean iFollow = followRepository.existsByFollowerIdAndFollowedId(userId, targetUserId);
        boolean theyFollow = followRepository.existsByFollowerIdAndFollowedId(targetUserId, userId);
        if (iFollow && theyFollow) {
            return ResponseEntity.ok(Map.of("status", "FRIENDS"));
        }

        // Check for pending request in either direction
        var sentRequest = friendRequestRepository.findBySenderIdAndReceiverId(userId, targetUserId);
        if (sentRequest.isPresent()) {
            return ResponseEntity.ok(Map.of("status", "REQUEST_SENT", "requestId", sentRequest.get().getId()));
        }

        var receivedRequest = friendRequestRepository.findBySenderIdAndReceiverId(targetUserId, userId);
        if (receivedRequest.isPresent() && "PENDING".equals(receivedRequest.get().getStatus())) {
            return ResponseEntity.ok(Map.of("status", "REQUEST_RECEIVED", "requestId", receivedRequest.get().getId()));
        }

        return ResponseEntity.ok(Map.of("status", "NONE"));
    }

    private void createFollowIfNotExists(long followerId, long followedId) {
        if (!followRepository.existsByFollowerIdAndFollowedId(followerId, followedId)) {
            var follow = new FollowEntity();
            follow.setFollowerId(followerId);
            follow.setFollowedId(followedId);
            followRepository.save(follow);
            eventPublisher.publishEvent(new FollowEvent(followerId, followedId, true));
        }
    }

    private List<FriendRequestDto> toDtoList(List<FriendRequestEntity> entities) {
        List<FriendRequestDto> dtos = new ArrayList<>();
        for (var entity : entities) {
            var sender = userService.getById(entity.getSenderId()).orElse(null);
            var receiver = userService.getById(entity.getReceiverId()).orElse(null);
            if (sender != null && receiver != null) {
                dtos.add(new FriendRequestDto(
                    entity.getId(),
                    sender.getId(), sender.getUsername(), sender.getDisplayName(), sender.getAvatarUrl(),
                    receiver.getId(), receiver.getUsername(), receiver.getDisplayName(), receiver.getAvatarUrl(),
                    entity.getStatus(),
                    entity.getCreatedAt()
                ));
            }
        }
        return dtos;
    }
}
