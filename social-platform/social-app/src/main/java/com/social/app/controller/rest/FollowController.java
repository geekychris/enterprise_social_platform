package com.social.app.controller.rest;

import com.social.app.persistence.entity.FollowEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.service.FollowEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follow")
public class FollowController {

    private final FollowRepository followRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FollowController(FollowRepository followRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.followRepository = followRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/{targetId}")
    public ResponseEntity<Void> follow(@PathVariable long targetId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        if (followRepository.existsByFollowerIdAndFollowedId(userId, targetId)) {
            return ResponseEntity.ok().build();
        }
        var entity = new FollowEntity();
        entity.setFollowerId(userId);
        entity.setFollowedId(targetId);
        followRepository.save(entity);
        eventPublisher.publishEvent(new FollowEvent(userId, targetId, true));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<Void> unfollow(@PathVariable long targetId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var id = new FollowEntity.FollowId(userId, targetId);
        if (followRepository.existsById(id)) {
            followRepository.deleteById(id);
            eventPublisher.publishEvent(new FollowEvent(userId, targetId, false));
        }
        return ResponseEntity.noContent().build();
    }
}
