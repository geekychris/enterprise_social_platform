package com.social.app.controller.graphql;

import com.social.app.persistence.entity.FollowEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.service.*;
import com.social.core.dto.CommentDto;
import com.social.core.dto.CreateCommentRequest;
import com.social.core.dto.CreatePostRequest;
import com.social.core.dto.PostDto;
import com.social.core.id.GlobalId;
import com.social.core.id.ObjectType;
import com.social.core.model.TargetType;
import com.social.core.model.Visibility;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Controller
public class MutationController {

    private final PostService postService;
    private final CommentService commentService;
    private final ReactionService reactionService;
    private final FollowRepository followRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityEventService entityEventService;

    public MutationController(PostService postService,
                              CommentService commentService,
                              ReactionService reactionService,
                              FollowRepository followRepository,
                              ApplicationEventPublisher eventPublisher,
                              EntityEventService entityEventService) {
        this.postService = postService;
        this.commentService = commentService;
        this.reactionService = reactionService;
        this.followRepository = followRepository;
        this.eventPublisher = eventPublisher;
        this.entityEventService = entityEventService;
    }

    @MutationMapping
    public PostDto createPost(@Argument String content,
                              @Argument String targetType,
                              @Argument Long targetId,
                              @Argument String visibility) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new IllegalStateException("Not authenticated");

        TargetType tt = targetType != null ? TargetType.valueOf(targetType) : null;
        Visibility vis = visibility != null ? Visibility.valueOf(visibility) : Visibility.PUBLIC;

        var request = new CreatePostRequest(content, tt, targetId, vis);
        var entity = postService.create(userId, request);
        return postService.toDto(entity, userId);
    }

    @MutationMapping
    public CommentDto createComment(@Argument long postId,
                                    @Argument Long parentCommentId,
                                    @Argument String content) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new IllegalStateException("Not authenticated");

        var request = new CreateCommentRequest(postId, parentCommentId, content, List.of());
        var entity = commentService.create(userId, request);
        return commentService.toDto(entity);
    }

    @MutationMapping
    public boolean react(@Argument long targetId, @Argument String reactionType) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new IllegalStateException("Not authenticated");

        String targetType;
        try {
            ObjectType objectType = GlobalId.typeOf(targetId);
            targetType = objectType.name();
        } catch (Exception e) {
            targetType = "POST";
        }

        reactionService.react(userId, targetId, targetType, reactionType);
        return true;
    }

    @MutationMapping
    public boolean follow(@Argument long targetId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new IllegalStateException("Not authenticated");

        if (!followRepository.existsByFollowerIdAndFollowedId(userId, targetId)) {
            var entity = new FollowEntity();
            entity.setFollowerId(userId);
            entity.setFollowedId(targetId);
            FollowEntity saved = followRepository.save(entity);
            eventPublisher.publishEvent(new FollowEvent(userId, targetId, true));
            try {
                entityEventService.publishFollowEvent("CREATE", userId, targetId, saved.getCreatedAt());
            } catch (Exception e) { /* don't affect main flow */ }
        }
        return true;
    }

    @MutationMapping
    public boolean unfollow(@Argument long targetId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new IllegalStateException("Not authenticated");

        var id = new FollowEntity.FollowId(userId, targetId);
        if (followRepository.existsById(id)) {
            followRepository.deleteById(id);
            eventPublisher.publishEvent(new FollowEvent(userId, targetId, false));
            try {
                entityEventService.publishFollowEvent("DELETE", userId, targetId, null);
            } catch (Exception e) { /* don't affect main flow */ }
        }
        return true;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
