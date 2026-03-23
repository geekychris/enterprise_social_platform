package com.social.datagen;

import com.social.datagen.generator.ContentGenerator.AuthorshipEdge;
import com.social.datagen.generator.CommentGenerator.CommentEdge;
import com.social.datagen.generator.ReactionGenerator.ReactionEdge;
import com.social.datagen.generator.SocialGraphGenerator.FollowEdge;
import com.social.datagen.generator.SocialGraphGenerator.MembershipEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Syncs generated edges to AOEE (Adjacency-Oriented Edge Engine) via REST calls.
 * Posts edges to the AOEE Spring proxy at localhost:8081.
 * Gracefully handles AOEE unavailability.
 */
@Component
public class AoeeSyncService {

    private static final Logger log = LoggerFactory.getLogger(AoeeSyncService.class);

    @Value("${datagen.aoee.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${datagen.aoee.batch-size:200}")
    private int batchSize;

    @Value("${datagen.aoee.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${datagen.aoee.read-timeout:30000}")
    private int readTimeout;

    private HttpClient httpClient;

    private HttpClient getClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .build();
        }
        return httpClient;
    }

    public void syncAll(List<FollowEdge> follows,
                        List<MembershipEdge> teamMemberships,
                        List<MembershipEdge> groupMemberships,
                        List<MembershipEdge> pageFollows,
                        List<AuthorshipEdge> authorshipEdges,
                        List<CommentEdge> commentEdges,
                        List<ReactionEdge> reactionEdges) {

        if (!isAoeeAvailable()) {
            log.warn("AOEE not available at {}. Skipping sync.", baseUrl);
            return;
        }

        log.info("Starting AOEE edge sync to {}", baseUrl);

        int total = follows.size() + teamMemberships.size() + groupMemberships.size()
                + pageFollows.size() + authorshipEdges.size() + commentEdges.size()
                + reactionEdges.size();
        log.info("Total edges to sync: {}", total);

        int synced = 0;

        // Sync follows
        log.info("Syncing {} follow edges...", follows.size());
        for (int i = 0; i < follows.size(); i += batchSize) {
            int end = Math.min(i + batchSize, follows.size());
            StringBuilder json = new StringBuilder("[");
            for (int j = i; j < end; j++) {
                FollowEdge e = follows.get(j);
                if (j > i) json.append(",");
                json.append(String.format(
                        "{\"sourceId\":%d,\"targetId\":%d,\"edgeType\":\"FOLLOWS\"}",
                        e.followerId(), e.followedId()));
            }
            json.append("]");
            postEdges(json.toString());
            synced += (end - i);
            if (synced % 1000 == 0) {
                log.info("  AOEE sync progress: {}/{}", synced, total);
            }
        }

        // Sync team memberships
        log.info("Syncing {} team membership edges...", teamMemberships.size());
        for (int i = 0; i < teamMemberships.size(); i += batchSize) {
            int end = Math.min(i + batchSize, teamMemberships.size());
            StringBuilder json = new StringBuilder("[");
            for (int j = i; j < end; j++) {
                MembershipEdge e = teamMemberships.get(j);
                if (j > i) json.append(",");
                json.append(String.format(
                        "{\"sourceId\":%d,\"targetId\":%d,\"edgeType\":\"MEMBER_OF\"}",
                        e.userId(), e.entityId()));
            }
            json.append("]");
            postEdges(json.toString());
            synced += (end - i);
        }

        // Sync group memberships
        log.info("Syncing {} group membership edges...", groupMemberships.size());
        for (int i = 0; i < groupMemberships.size(); i += batchSize) {
            int end = Math.min(i + batchSize, groupMemberships.size());
            StringBuilder json = new StringBuilder("[");
            for (int j = i; j < end; j++) {
                MembershipEdge e = groupMemberships.get(j);
                if (j > i) json.append(",");
                json.append(String.format(
                        "{\"sourceId\":%d,\"targetId\":%d,\"edgeType\":\"MEMBER_OF\"}",
                        e.userId(), e.entityId()));
            }
            json.append("]");
            postEdges(json.toString());
            synced += (end - i);
        }

        // Sync page follows
        log.info("Syncing {} page follow edges...", pageFollows.size());
        for (int i = 0; i < pageFollows.size(); i += batchSize) {
            int end = Math.min(i + batchSize, pageFollows.size());
            StringBuilder json = new StringBuilder("[");
            for (int j = i; j < end; j++) {
                MembershipEdge e = pageFollows.get(j);
                if (j > i) json.append(",");
                json.append(String.format(
                        "{\"sourceId\":%d,\"targetId\":%d,\"edgeType\":\"FOLLOWS\"}",
                        e.userId(), e.entityId()));
            }
            json.append("]");
            postEdges(json.toString());
            synced += (end - i);
        }

        // Sync authorship
        log.info("Syncing {} authorship edges...", authorshipEdges.size());
        for (int i = 0; i < authorshipEdges.size(); i += batchSize) {
            int end = Math.min(i + batchSize, authorshipEdges.size());
            StringBuilder json = new StringBuilder("[");
            for (int j = i; j < end; j++) {
                AuthorshipEdge e = authorshipEdges.get(j);
                if (j > i) json.append(",");
                json.append(String.format(
                        "{\"sourceId\":%d,\"targetId\":%d,\"edgeType\":\"AUTHORED\"}",
                        e.authorId(), e.postId()));
            }
            json.append("]");
            postEdges(json.toString());
            synced += (end - i);
            if (synced % 1000 == 0) {
                log.info("  AOEE sync progress: {}/{}", synced, total);
            }
        }

        // Sync comment_on edges
        log.info("Syncing {} comment edges...", commentEdges.size());
        for (int i = 0; i < commentEdges.size(); i += batchSize) {
            int end = Math.min(i + batchSize, commentEdges.size());
            StringBuilder json = new StringBuilder("[");
            for (int j = i; j < end; j++) {
                CommentEdge e = commentEdges.get(j);
                if (j > i) json.append(",");
                json.append(String.format(
                        "{\"sourceId\":%d,\"targetId\":%d,\"edgeType\":\"COMMENT_ON\"}",
                        e.commentId(), e.postId()));
            }
            json.append("]");
            postEdges(json.toString());
            synced += (end - i);
            if (synced % 1000 == 0) {
                log.info("  AOEE sync progress: {}/{}", synced, total);
            }
        }

        // Sync reaction/like edges
        log.info("Syncing {} reaction edges...", reactionEdges.size());
        for (int i = 0; i < reactionEdges.size(); i += batchSize) {
            int end = Math.min(i + batchSize, reactionEdges.size());
            StringBuilder json = new StringBuilder("[");
            for (int j = i; j < end; j++) {
                ReactionEdge e = reactionEdges.get(j);
                if (j > i) json.append(",");
                json.append(String.format(
                        "{\"sourceId\":%d,\"targetId\":%d,\"edgeType\":\"LIKES\",\"metadata\":%d}",
                        e.userId(), e.targetId(), e.type().aoeeMetadata()));
            }
            json.append("]");
            postEdges(json.toString());
            synced += (end - i);
            if (synced % 1000 == 0) {
                log.info("  AOEE sync progress: {}/{}", synced, total);
            }
        }

        log.info("AOEE sync complete. Total edges synced: {}", synced);
    }

    private boolean isAoeeAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/actuator/health"))
                    .timeout(Duration.ofMillis(connectTimeout))
                    .GET()
                    .build();
            HttpResponse<String> response = getClient().send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            log.debug("AOEE health check failed: {}", e.getMessage());
            return false;
        }
    }

    private void postEdges(String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/edges"))
                    .timeout(Duration.ofMillis(readTimeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = getClient().send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("AOEE edge POST returned status {}: {}",
                        response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to sync edges to AOEE: {}", e.getMessage());
        }
    }
}
