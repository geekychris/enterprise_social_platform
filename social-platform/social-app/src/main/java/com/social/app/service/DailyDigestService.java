package com.social.app.service;

import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class DailyDigestService {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestService.class);

    @Value("${social.bot.user-id}")
    private long botUserId;

    @Value("${social.bot.digest.lookback-hours:12}")
    private int lookbackHours;

    @Value("${social.bot.digest.enabled:true}")
    private boolean digestEnabled;

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final PostRepository postRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final OllamaService ollamaService;

    // Simple in-memory tracking (production would use daily_digest_log table)
    private LocalDate lastDigestDate;

    public DailyDigestService(UserRepository userRepository, MembershipRepository membershipRepository,
                              GroupRepository groupRepository, PostRepository postRepository,
                              MessageRepository messageRepository, MessageService messageService,
                              ConversationService conversationService, OllamaService ollamaService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
        this.postRepository = postRepository;
        this.messageRepository = messageRepository;
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.ollamaService = ollamaService;
    }

    @Scheduled(cron = "${social.bot.digest.cron:0 0 8 * * *}")
    public void sendDailyDigests() {
        if (!digestEnabled) return;

        LocalDate today = LocalDate.now();
        if (today.equals(lastDigestDate)) return;
        lastDigestDate = today;

        log.info("Starting daily digest generation");

        var users = userRepository.findAll().stream()
                .filter(u -> !u.isBot() && u.isDigestEnabled())
                .toList();

        int sent = 0;
        for (var user : users) {
            try {
                String digest = buildDigest(user.getId());
                if (digest != null) {
                    sendDigestToUser(user.getId(), digest);
                    sent++;
                }
            } catch (Exception e) {
                log.warn("Failed to send digest to user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("Daily digest complete: sent to {} of {} users", sent, users.size());
    }

    /**
     * Can also be triggered manually for a user.
     */
    @Transactional
    public String buildDigest(long userId) {
        Instant since = Instant.now().minus(lookbackHours, ChronoUnit.HOURS);
        List<String> sections = new ArrayList<>();

        // Unread messages
        long unread = messageRepository.countUnreadForUser(userId);
        if (unread > 0) {
            sections.add("**Messages:** You have " + unread + " unread message" + (unread > 1 ? "s" : ""));
        }

        // Group activity
        var memberships = membershipRepository.findByUserIdAndStatus(userId, "APPROVED");
        for (var m : memberships) {
            var group = groupRepository.findById(m.getGroupId()).orElse(null);
            if (group == null) continue;

            long newPosts = postRepository.findByTargetIdOrderByCreatedAtDesc(group.getId()).stream()
                    .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(since))
                    .count();
            if (newPosts > 0) {
                sections.add("**" + group.getName() + ":** " + newPosts + " new post" + (newPosts > 1 ? "s" : ""));
            }
        }

        if (sections.isEmpty()) return null;

        StringBuilder raw = new StringBuilder();
        raw.append("Good morning! Here's your daily WorkSphere digest:\n\n");
        for (String s : sections) {
            raw.append("- ").append(s).append("\n");
        }
        raw.append("\nHave a great day!");

        // Optionally polish with LLM
        try {
            StringBuilder polished = new StringBuilder();
            ollamaService.chatBlocking(
                    "You are a friendly workplace assistant. Rewrite this daily digest to be warm and concise. Keep the markdown formatting. Don't add information that isn't there.",
                    raw.toString(),
                    polished
            );
            if (polished.length() > 20) return polished.toString();
        } catch (Exception e) {
            log.debug("LLM polish failed, using raw digest");
        }

        return raw.toString();
    }

    private void sendDigestToUser(long userId, String content) {
        var conversation = conversationService.getOrCreateDirect(botUserId, userId);
        messageService.send(botUserId, conversation.getId(), content, null);
    }
}
