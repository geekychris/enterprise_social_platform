package com.social.app.service;

import com.social.app.persistence.entity.ConversationEntity;
import com.social.app.persistence.entity.ConversationParticipantEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.ConversationParticipantRepository;
import com.social.app.persistence.repository.ConversationRepository;
import com.social.app.persistence.repository.MessageRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.ConversationDto;
import com.social.core.dto.MessageDto;
import com.social.core.dto.UserSummaryDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final UserRepository userRepository;
    private final GlobalIdGenerator idGenerator;
    private final UnreadCountService unreadCountService;
    private final CacheService cacheService;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationParticipantRepository participantRepository,
                               MessageRepository messageRepository,
                               MessageService messageService,
                               UserRepository userRepository,
                               GlobalIdGenerator idGenerator,
                               UnreadCountService unreadCountService,
                               CacheService cacheService) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
        this.unreadCountService = unreadCountService;
        this.cacheService = cacheService;
    }

    /**
     * Create a new conversation. If exactly one other participant and no name,
     * returns existing DIRECT conversation if one exists.
     */
    @Transactional
    public ConversationEntity create(long creatorId, List<Long> participantIds, String name) {
        // Deduplicate: for DMs (1 other participant, no name), reuse existing
        if (participantIds.size() == 1 && (name == null || name.isBlank())) {
            long otherId = participantIds.get(0);
            Optional<ConversationEntity> existing = conversationRepository.findDirectConversation(creatorId, otherId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String type = (participantIds.size() == 1 && (name == null || name.isBlank())) ? "DIRECT" : "GROUP";

        var conversation = new ConversationEntity();
        conversation.setId(idGenerator.next(ObjectType.CONVERSATION).value());
        conversation.setType(type);
        conversation.setName(name);
        conversation.setCreatedBy(creatorId);
        conversationRepository.saveAndFlush(conversation);

        // Add creator as participant
        addParticipantInternal(conversation.getId(), creatorId);

        // Add other participants
        for (Long participantId : participantIds) {
            if (!participantId.equals(creatorId)) {
                addParticipantInternal(conversation.getId(), participantId);
            }
        }

        // Ensure unread count records for all participants
        try {
            unreadCountService.ensureRecord(conversation.getId(), creatorId);
            for (Long participantId : participantIds) {
                if (!participantId.equals(creatorId)) {
                    unreadCountService.ensureRecord(conversation.getId(), participantId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to ensure unread count records for conversation {}: {}", conversation.getId(), e.getMessage());
        }

        return conversation;
    }

    /**
     * Get or create a direct conversation between two users.
     */
    @Transactional
    public ConversationEntity getOrCreateDirect(long user1, long user2) {
        return conversationRepository.findDirectConversation(user1, user2)
                .orElseGet(() -> create(user1, List.of(user2), null));
    }

    @SuppressWarnings("unchecked")
    public List<ConversationDto> getConversationsForUser(long userId) {
        String cacheKey = "convList:" + userId;
        try {
            List<ConversationDto> cached = (List<ConversationDto>) cacheService.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Failed to read conversation list from cache for user {}: {}", userId, e.getMessage());
        }

        List<ConversationEntity> conversations = conversationRepository.findUserConversations(userId);
        List<ConversationDto> result = new ArrayList<>();

        for (ConversationEntity conv : conversations) {
            List<ConversationParticipantEntity> participants = participantRepository.findByConversationId(conv.getId());
            List<UserSummaryDto> participantDtos = participants.stream()
                    .map(p -> getUserSummary(p.getUserId()))
                    .toList();

            // Get latest message
            var latestMessages = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conv.getId(), org.springframework.data.domain.PageRequest.of(0, 1));
            MessageDto lastMessage = latestMessages.isEmpty() ? null : messageService.toDto(latestMessages.get(0));

            long unreadCount = messageRepository.countUnreadInConversation(conv.getId(), userId);

            result.add(new ConversationDto(
                    conv.getId(),
                    conv.getName(),
                    conv.getType(),
                    participantDtos,
                    lastMessage,
                    unreadCount,
                    conv.getCreatedAt()
            ));
        }

        try {
            cacheService.put(cacheKey, result, Duration.ofSeconds(10));
        } catch (Exception e) {
            log.warn("Failed to cache conversation list for user {}: {}", userId, e.getMessage());
        }

        return result;
    }

    public ConversationDto getConversation(long conversationId, long userId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        verifyParticipant(conversationId, userId);

        List<UserSummaryDto> participantDtos = participantRepository.findByConversationId(conversationId).stream()
                .map(p -> getUserSummary(p.getUserId()))
                .toList();

        var latestMessages = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, org.springframework.data.domain.PageRequest.of(0, 1));
        MessageDto lastMessage = latestMessages.isEmpty() ? null : messageService.toDto(latestMessages.get(0));

        long unreadCount = messageRepository.countUnreadInConversation(conversationId, userId);

        return new ConversationDto(
                conv.getId(),
                conv.getName(),
                conv.getType(),
                participantDtos,
                lastMessage,
                unreadCount,
                conv.getCreatedAt()
        );
    }

    @Transactional
    public void rename(long conversationId, long userId, String name) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        verifyParticipant(conversationId, userId);

        conv.setName(name);
        conversationRepository.save(conv);
    }

    /**
     * Add a participant to a conversation. If the conversation is DIRECT, it is
     * automatically upgraded to GROUP. The shareHistory flag controls whether the
     * new participant can see messages from before they joined.
     */
    @Transactional
    public void addParticipant(long conversationId, long requesterId, long userId, boolean shareHistory) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        verifyParticipant(conversationId, requesterId);

        // Auto-upgrade DIRECT to GROUP when adding a third person
        if ("DIRECT".equals(conv.getType())) {
            conv.setType("GROUP");
            conversationRepository.save(conv);
        }

        addParticipantInternal(conversationId, userId, shareHistory);
    }

    @Transactional
    public void removeParticipant(long conversationId, long requesterId, long userId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        verifyParticipant(conversationId, requesterId);

        participantRepository.deleteById(new ConversationParticipantEntity.ParticipantId(conversationId, userId));
    }

    @Transactional
    public void markRead(long conversationId, long userId) {
        verifyParticipant(conversationId, userId);
        participantRepository.updateLastReadAt(conversationId, userId, Instant.now());
        try {
            unreadCountService.resetUnread(conversationId, userId);
        } catch (Exception e) {
            log.warn("Failed to reset unread count for conversation {} user {}: {}", conversationId, userId, e.getMessage());
        }
    }

    public ConversationParticipantEntity verifyParticipant(long conversationId, long userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a participant in this conversation"));
    }

    private void addParticipantInternal(long conversationId, long userId) {
        addParticipantInternal(conversationId, userId, true);
    }

    private void addParticipantInternal(long conversationId, long userId, boolean shareHistory) {
        var existing = participantRepository.findByConversationIdAndUserId(conversationId, userId);
        if (existing.isPresent()) return;

        var participant = new ConversationParticipantEntity();
        participant.setConversationId(conversationId);
        participant.setUserId(userId);
        // If not sharing history, set visible_from to now so they only see future messages
        if (!shareHistory) {
            participant.setVisibleFrom(Instant.now());
        }
        participantRepository.save(participant);

        try {
            unreadCountService.ensureRecord(conversationId, userId);
        } catch (Exception e) {
            log.warn("Failed to ensure unread count record for conversation {} user {}: {}", conversationId, userId, e.getMessage());
        }
    }

    private UserSummaryDto getUserSummary(long userId) {
        Optional<UserEntity> user = userRepository.findById(userId);
        if (user.isPresent()) {
            UserEntity u = user.get();
            return new UserSummaryDto(u.getId(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl());
        }
        return new UserSummaryDto(userId, "unknown", "Unknown User", null);
    }
}
