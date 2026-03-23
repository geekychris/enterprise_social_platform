package com.social.app.controller.rest;

import com.social.app.graph.AoeeGraphClient;
import com.social.app.service.ReactionService;
import com.social.core.dto.ReactorDto;
import com.social.core.id.GlobalId;
import com.social.core.id.ObjectType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reactions")
public class ReactionController {

    private final ReactionService reactionService;
    private final AoeeGraphClient aoeeGraphClient;

    public ReactionController(ReactionService reactionService, AoeeGraphClient aoeeGraphClient) {
        this.reactionService = reactionService;
        this.aoeeGraphClient = aoeeGraphClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> react(@RequestBody Map<String, Object> body,
                                                     Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        Object rawTargetId = body.get("targetId");
        long targetId = rawTargetId instanceof Number n ? n.longValue() : Long.parseLong(rawTargetId.toString());
        String reactionType = (String) body.get("reactionType");

        // Determine target type from GlobalId encoding
        String targetType;
        try {
            ObjectType objectType = GlobalId.typeOf(targetId);
            targetType = objectType.name();
        } catch (Exception e) {
            targetType = "POST";
        }

        var reaction = reactionService.react(userId, targetId, targetType, reactionType);
        return ResponseEntity.ok(Map.of(
                "id", reaction.getId(),
                "targetId", reaction.getTargetId(),
                "reactionType", reaction.getReactionType()
        ));
    }

    @GetMapping("/{targetId}/users")
    public ResponseEntity<List<ReactorDto>> getReactors(@PathVariable long targetId,
                                                         Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(reactionService.getReactors(targetId, userId));
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<Void> unreact(@PathVariable long targetId, Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        reactionService.unreact(userId, targetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/aoee/{targetId}/likers")
    public ResponseEntity<Map<String, Object>> getAoeeLikers(@PathVariable long targetId) {
        List<Long> likerIds = aoeeGraphClient.getNeighbors(targetId, "LIKES");
        long count = aoeeGraphClient.count(targetId, "LIKES");
        return ResponseEntity.ok(Map.of(
            "targetId", targetId,
            "likerIds", likerIds,
            "count", count,
            "source", "aoee"
        ));
    }

    @GetMapping("/aoee/user/{userId}/likes")
    public ResponseEntity<Map<String, Object>> getAoeeUserLikes(@PathVariable long userId) {
        List<Long> likedTargetIds = aoeeGraphClient.getNeighbors(userId, "LIKES");
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "likedTargetIds", likedTargetIds,
            "count", likedTargetIds.size(),
            "source", "aoee"
        ));
    }

    @GetMapping("/aoee/{targetId}/check/{userId}")
    public ResponseEntity<Map<String, Object>> checkAoeeLike(@PathVariable long targetId, @PathVariable long userId) {
        boolean exists = aoeeGraphClient.contains(userId, "LIKES", targetId);
        return ResponseEntity.ok(Map.of(
            "targetId", targetId,
            "userId", userId,
            "liked", exists,
            "source", "aoee"
        ));
    }
}
