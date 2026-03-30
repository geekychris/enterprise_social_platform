package com.social.app.controller.rest;

import com.social.app.persistence.entity.FollowEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.service.EntityEventService;
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
    private final EntityEventService entityEventService;

    public FollowController(FollowRepository followRepository,
                            ApplicationEventPublisher eventPublisher,
                            EntityEventService entityEventService) {
        this.followRepository = followRepository;
        this.eventPublisher = eventPublisher;
        this.entityEventService = entityEventService;
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
        FollowEntity saved = followRepository.save(entity);
        eventPublisher.publishEvent(new FollowEvent(userId, targetId, true));
        try {
            entityEventService.publishFollowEvent("CREATE", userId, targetId, saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<Void> unfollow(@PathVariable long targetId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        var id = new FollowEntity.FollowId(userId, targetId);
        if (followRepository.existsById(id)) {
            followRepository.deleteById(id);
            eventPublisher.publishEvent(new FollowEvent(userId, targetId, false));
            try {
                entityEventService.publishFollowEvent("DELETE", userId, targetId, null);
            } catch (Exception e) { /* don't affect main flow */ }
        }
        return ResponseEntity.noContent().build();
    }
}
