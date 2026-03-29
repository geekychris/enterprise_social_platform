package com.social.app.service;

import com.social.app.persistence.entity.MessageEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.AttachmentRepository;
import com.social.app.persistence.repository.MessageRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.AttachmentDto;
import com.social.core.dto.MessageDto;
import com.social.core.dto.UserSummaryDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final GlobalIdGenerator idGenerator;
    private final JdbcTemplate jdbc;
    private final ConversationSummaryService conversationSummaryService;
    private final UnreadCountService unreadCountService;
    private final MessageBroadcastService messageBroadcastService;
    private final EventPublisher eventPublisher;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          AttachmentRepository attachmentRepository,
                          AttachmentService attachmentService,
                          GlobalIdGenerator idGenerator,
                          JdbcTemplate jdbc,
                          ConversationSummaryService conversationSummaryService,
                          UnreadCountService unreadCountService,
                          MessageBroadcastService messageBroadcastService,
                          EventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
        this.idGenerator = idGenerator;
        this.jdbc = jdbc;
        this.conversationSummaryService = conversationSummaryService;
        this.unreadCountService = unreadCountService;
        this.messageBroadcastService = messageBroadcastService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MessageEntity send(long senderId, long conversationId, String content, List<Long> attachmentIds) {
        var entity = new MessageEntity();
        entity.setId(idGenerator.next(ObjectType.MESSAGE).value());
        entity.setConversationId(conversationId);
        entity.setSenderId(senderId);
        entity.setContent(content);
        entity.setRead(false);

        MessageEntity saved = messageRepository.saveAndFlush(entity);

        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            linkAttachments(saved.getId(), attachmentIds);
        }

        // Scalability service integrations - fail gracefully
        try {
            conversationSummaryService.updateSummary(conversationId, saved.getId(), senderId, content, saved.getCreatedAt());
        } catch (Exception e) {
            log.warn("Failed to update conversation summary for conversation {}: {}", conversationId, e.getMessage());
        }
        try {
            unreadCountService.incrementUnread(conversationId, senderId);
        } catch (Exception e) {
            log.warn("Failed to increment unread count for conversation {}: {}", conversationId, e.getMessage());
        }
        try {
            messageBroadcastService.broadcastMessage(toDto(saved), conversationId);
        } catch (Exception e) {
            log.warn("Failed to broadcast message for conversation {}: {}", conversationId, e.getMessage());
        }
        try {
            eventPublisher.publishMessageSent(conversationId, senderId, saved.getId(), content);
        } catch (Exception e) {
            log.warn("Failed to publish message sent event for conversation {}: {}", conversationId, e.getMessage());
        }

        return saved;
    }

    private void linkAttachments(long messageId, List<Long> attachmentIds) {
        for (Long attachmentId : attachmentIds) {
            jdbc.update("INSERT INTO message_attachments (message_id, attachment_id) VALUES (?, ?)",
                    messageId, attachmentId);
        }
    }

    public List<MessageDto> getMessages(long conversationId, Instant visibleFrom, int page, int size) {
        List<MessageEntity> messages;
        if (visibleFrom != null) {
            messages = messageRepository.findByConversationIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    conversationId, visibleFrom, PageRequest.of(page, size));
        } else {
            messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(page, size));
        }
        return messages.stream().map(this::toDto).toList();
    }

    public long getUnreadCount(long userId) {
        return messageRepository.countUnreadForUser(userId);
    }

    public MessageDto toDto(MessageEntity entity) {
        UserSummaryDto sender = getUserSummary(entity.getSenderId());

        List<Long> attachmentIds = jdbc.queryForList(
                "SELECT attachment_id FROM message_attachments WHERE message_id = ?",
                Long.class, entity.getId());
        List<AttachmentDto> attachments = attachmentIds.isEmpty() ? List.of() :
                attachmentRepository.findByIdIn(attachmentIds).stream()
                        .map(attachmentService::toDto)
                        .toList();

        return new MessageDto(
                entity.getId(),
                entity.getConversationId(),
                sender,
                entity.getContent(),
                attachments,
                entity.isRead(),
                entity.getCreatedAt()
        );
    }

    private UserSummaryDto getUserSummary(long userId) {
        Optional<UserEntity> user = userRepository.findById(userId);
        if (user.isPresent()) {
            UserEntity u = user.get();
            return new UserSummaryDto(
                    u.getId(),
                    u.getUsername(),
                    u.getDisplayName(),
                    u.getAvatarUrl()
            );
        }
        return new UserSummaryDto(userId, "unknown", "Unknown User", null);
    }
}
