package com.social.app.service;

import com.social.app.persistence.entity.NotificationEntity;
import com.social.app.persistence.repository.NotificationRepository;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^\\]]+)\\]\\((\\d+)\\)");

    private final NotificationRepository notificationRepository;
    private final GlobalIdGenerator idGenerator;
    private final UserService userService;

    public NotificationService(NotificationRepository notificationRepository,
                                GlobalIdGenerator idGenerator,
                                UserService userService) {
        this.notificationRepository = notificationRepository;
        this.idGenerator = idGenerator;
        this.userService = userService;
    }

    @Transactional
    public void createNotification(long userId, String type, Long actorId, Long targetId, String targetType, String message) {
        // Don't notify yourself
        if (actorId != null && actorId == userId) return;

        var entity = new NotificationEntity();
        entity.setId(idGenerator.next(ObjectType.MESSAGE).value()); // reuse MESSAGE type for IDs
        entity.setUserId(userId);
        entity.setType(type);
        entity.setActorId(actorId);
        entity.setTargetId(targetId);
        entity.setTargetType(targetType);
        entity.setMessage(message);
        notificationRepository.save(entity);
    }

    /**
     * Parse @mentions from text and create MENTION notifications.
     */
    @Transactional
    public void processMentions(String text, long actorId, Long targetId, String targetType) {
        if (text == null) return;

        String actorName = userService.getById(actorId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Someone");

        Matcher matcher = MENTION_PATTERN.matcher(text);
        Set<Long> notified = new HashSet<>();

        while (matcher.find()) {
            try {
                long mentionedUserId = Long.parseLong(matcher.group(2));
                if (mentionedUserId != actorId && notified.add(mentionedUserId)) {
                    createNotification(
                            mentionedUserId,
                            "MENTION",
                            actorId,
                            targetId,
                            targetType,
                            actorName + " mentioned you in a comment"
                    );
                }
            } catch (NumberFormatException e) {
                // skip invalid mention
            }
        }
    }

    /**
     * Create a REACTION notification.
     */
    @Transactional
    public void notifyReaction(long postAuthorId, long actorId, long targetId, String reactionType) {
        String actorName = userService.getById(actorId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Someone");
        String emoji = switch (reactionType) {
            case "LIKE" -> "\uD83D\uDC4D";
            case "LOVE" -> "\u2764\uFE0F";
            case "HAHA" -> "\uD83D\uDE02";
            case "WOW" -> "\uD83D\uDE2E";
            case "SAD" -> "\uD83D\uDE22";
            case "ANGRY" -> "\uD83D\uDE20";
            default -> "";
        };
        createNotification(postAuthorId, "REACTION", actorId, targetId, "POST",
                actorName + " reacted " + emoji + " to your post");
    }

    /**
     * Create a COMMENT notification for the post author.
     */
    @Transactional
    public void notifyComment(long postAuthorId, long actorId, long postId) {
        String actorName = userService.getById(actorId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Someone");
        createNotification(postAuthorId, "COMMENT", actorId, postId, "POST",
                actorName + " commented on your post");
    }

    /**
     * Create a FRIEND_REQUEST notification.
     */
    @Transactional
    public void notifyFriendRequest(long receiverId, long senderId) {
        String senderName = userService.getById(senderId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Someone");
        createNotification(receiverId, "FRIEND_REQUEST", senderId, null, null,
                senderName + " sent you a friend request");
    }

    /**
     * Create a FRIEND_ACCEPTED notification.
     */
    @Transactional
    public void notifyFriendAccepted(long senderId, long accepterId) {
        String accepterName = userService.getById(accepterId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Someone");
        createNotification(senderId, "FRIEND_ACCEPTED", accepterId, null, null,
                accepterName + " accepted your friend request");
    }

    public List<NotificationEntity> getNotifications(long userId, int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }

    public long getUnreadCount(long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(long userId) {
        notificationRepository.markAllRead(userId);
    }
}
