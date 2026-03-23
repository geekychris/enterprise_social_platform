package com.social.app.graph;

import com.social.app.service.FollowEvent;
import com.social.app.service.MembershipEvent;
import com.social.app.service.PostCreatedEvent;
import com.social.app.service.ReactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Listens to application events and syncs edges to the AOEE social graph cache.
 * All operations are async to avoid blocking the main request.
 */
@Service
public class GraphSyncService {

    private static final Logger log = LoggerFactory.getLogger(GraphSyncService.class);

    private final AoeeGraphClient aoeeClient;

    public GraphSyncService(AoeeGraphClient aoeeClient) {
        this.aoeeClient = aoeeClient;
    }

    @Async
    @EventListener
    public void onFollow(FollowEvent event) {
        if (event.followed()) {
            log.debug("Syncing FOLLOWS edge: {} -> {}", event.followerId(), event.followedId());
            aoeeClient.addEdge(event.followerId(), "FOLLOWS", event.followedId());
        } else {
            log.debug("Removing FOLLOWS edge: {} -> {}", event.followerId(), event.followedId());
            aoeeClient.removeEdge(event.followerId(), "FOLLOWS", event.followedId());
        }
    }

    @Async
    @EventListener
    public void onPostCreated(PostCreatedEvent event) {
        log.debug("Syncing AUTHORED edge: {} -> {}", event.authorId(), event.postId());
        aoeeClient.addEdge(event.authorId(), "AUTHORED", event.postId());

        if (event.targetId() != null) {
            log.debug("Syncing CONTAINS edge: {} -> {}", event.targetId(), event.postId());
            aoeeClient.addEdge(event.targetId(), "CONTAINS", event.postId());
        }
    }

    @Async
    @EventListener
    public void onReaction(ReactionEvent event) {
        if (event.added()) {
            log.debug("Syncing LIKES edge: {} -> {}", event.userId(), event.targetId());
            aoeeClient.addEdge(event.userId(), "LIKES", event.targetId());
        } else {
            log.debug("Removing LIKES edge: {} -> {}", event.userId(), event.targetId());
            aoeeClient.removeEdge(event.userId(), "LIKES", event.targetId());
        }
    }

    @Async
    @EventListener
    public void onMembership(MembershipEvent event) {
        if (event.joined()) {
            log.debug("Syncing MEMBER_OF edge: {} -> {}", event.userId(), event.groupId());
            aoeeClient.addEdge(event.userId(), "MEMBER_OF", event.groupId());
        } else {
            log.debug("Removing MEMBER_OF edge: {} -> {}", event.userId(), event.groupId());
            aoeeClient.removeEdge(event.userId(), "MEMBER_OF", event.groupId());
        }
    }
}
