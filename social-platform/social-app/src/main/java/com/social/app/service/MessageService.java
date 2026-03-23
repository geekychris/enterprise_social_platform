package com.social.app.service;

import com.social.app.persistence.entity.MessageEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.AttachmentRepository;
import com.social.app.persistence.repository.MessageRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.AttachmentDto;
import com.social.core.dto.ConversationDto;
import com.social.core.dto.MessageDto;
import com.social.core.dto.UserSummaryDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final GlobalIdGenerator idGenerator;
    private final JdbcTemplate jdbc;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          AttachmentRepository attachmentRepository,
                          AttachmentService attachmentService,
                          GlobalIdGenerator idGenerator,
                          JdbcTemplate jdbc) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
        this.idGenerator = idGenerator;
        this.jdbc = jdbc;
    }

    @Transactional
    public MessageEntity send(long senderId, long recipientId, String content, List<Long> attachmentIds) {
        var entity = new MessageEntity();
        entity.setId(idGenerator.next(ObjectType.MESSAGE).value());
        entity.setSenderId(senderId);
        entity.setRecipientId(recipientId);
        entity.setContent(content);
        entity.setRead(false);

        MessageEntity saved = messageRepository.saveAndFlush(entity);

        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            linkAttachments(saved.getId(), attachmentIds);
        }

        return saved;
    }

    private void linkAttachments(long messageId, List<Long> attachmentIds) {
        for (Long attachmentId : attachmentIds) {
            jdbc.update("INSERT INTO message_attachments (message_id, attachment_id) VALUES (?, ?)",
                    messageId, attachmentId);
        }
    }

    public List<ConversationDto> getConversations(long userId) {
        List<MessageEntity> latestMessages = messageRepository.findLatestPerConversation(userId);
        List<ConversationDto> conversations = new ArrayList<>();

        for (MessageEntity message : latestMessages) {
            long partnerId = message.getSenderId().equals(userId) ? message.getRecipientId() : message.getSenderId();
            UserSummaryDto partner = getUserSummary(partnerId);

            // Count unread from this partner
            long unreadCount = messageRepository.findConversation(userId, partnerId, PageRequest.of(0, 1000))
                    .stream()
                    .filter(m -> m.getRecipientId().equals(userId) && !m.isRead())
                    .count();

            conversations.add(new ConversationDto(partner, toDto(message), unreadCount));
        }

        return conversations;
    }

    public List<MessageDto> getConversation(long userId, long partnerId, int page, int size) {
        return messageRepository.findConversation(userId, partnerId, PageRequest.of(page, size)).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void markRead(long userId, long senderId) {
        messageRepository.markConversationRead(senderId, userId);
    }

    public long getUnreadCount(long userId) {
        return messageRepository.countByRecipientIdAndReadFalse(userId);
    }

    public MessageDto toDto(MessageEntity entity) {
        UserSummaryDto sender = getUserSummary(entity.getSenderId());
        UserSummaryDto recipient = getUserSummary(entity.getRecipientId());

        List<Long> attachmentIds = jdbc.queryForList(
                "SELECT attachment_id FROM message_attachments WHERE message_id = ?",
                Long.class, entity.getId());
        List<AttachmentDto> attachments = attachmentIds.isEmpty() ? List.of() :
                attachmentRepository.findByIdIn(attachmentIds).stream()
                        .map(attachmentService::toDto)
                        .toList();

        return new MessageDto(
                entity.getId(),
                sender,
                recipient,
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
