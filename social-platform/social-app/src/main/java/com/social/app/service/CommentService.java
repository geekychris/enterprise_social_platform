package com.social.app.service;

import com.social.app.persistence.entity.CommentEntity;
import com.social.app.persistence.repository.AttachmentRepository;
import com.social.app.persistence.repository.CommentRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.core.dto.CommentDto;
import com.social.core.dto.CreateCommentRequest;
import com.social.core.dto.UserSummaryDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final GlobalIdGenerator idGenerator;
    private final UserService userService;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final JdbcTemplate jdbc;
    private final EntityEventService entityEventService;
    private final AppService appService;
    private final CacheService cacheService;
    private final MessageBroadcastService broadcastService;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          GlobalIdGenerator idGenerator,
                          UserService userService,
                          AttachmentRepository attachmentRepository,
                          AttachmentService attachmentService,
                          JdbcTemplate jdbc,
                          EntityEventService entityEventService,
                          AppService appService,
                          CacheService cacheService,
                          MessageBroadcastService broadcastService) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.idGenerator = idGenerator;
        this.userService = userService;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
        this.jdbc = jdbc;
        this.entityEventService = entityEventService;
        this.appService = appService;
        this.cacheService = cacheService;
        this.broadcastService = broadcastService;
    }

    @Transactional
    public CommentEntity create(long authorId, CreateCommentRequest request) {
        short depth = 0;
        if (request.parentCommentId() != null) {
            CommentEntity parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found: " + request.parentCommentId()));
            if (parent.getDepth() >= 1) {
                throw new IllegalArgumentException("Maximum comment depth of 1 exceeded. Cannot reply to a reply.");
            }
            depth = 1;
        }

        var entity = new CommentEntity();
        entity.setId(idGenerator.next(ObjectType.COMMENT).value());
        entity.setPostId(request.postId());
        entity.setParentCommentId(request.parentCommentId());
        entity.setAuthorId(authorId);
        entity.setContent(request.content());
        entity.setDepth(depth);
        CommentEntity saved = commentRepository.save(entity);

        if (request.attachmentIds() != null && !request.attachmentIds().isEmpty()) {
            for (Long attId : request.attachmentIds()) {
                jdbc.update("INSERT INTO comment_attachments (comment_id, attachment_id) VALUES (?, ?)",
                        saved.getId(), attId);
            }
        }

        try {
            entityEventService.publishCommentEvent("CREATE", saved.getId(), saved.getPostId(),
                saved.getAuthorId(), saved.getContent(), saved.getParentCommentId(),
                saved.getDepth(), saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }

        try {
            var commentPayload = Map.of(
                "commentId", (Object) saved.getId(),
                "postId", saved.getPostId(),
                "authorId", saved.getAuthorId(),
                "content", saved.getContent() != null ? saved.getContent() : ""
            );
            // Publish at POST level
            appService.publishEvent("COMMENT_CREATED", "POST", saved.getPostId(), commentPayload);
            // Also publish at PAGE/GROUP level so page-installed apps receive comments
            var post = postRepository.findById(saved.getPostId()).orElse(null);
            if (post != null && post.getTargetType() != null && post.getTargetId() != null) {
                appService.publishEvent("COMMENT_CREATED", post.getTargetType(), post.getTargetId(), commentPayload);
            }
        } catch (Exception e) { /* don't block comment creation */ }

        // Evict parent post cache so comment count updates
        cacheService.evictPattern("post:" + saved.getPostId() + ":*");

        // Broadcast to anyone viewing this post (real-time comment update via WS gateway)
        try {
            broadcastService.broadcastPostUpdate(saved.getPostId(), "COMMENT_ADDED",
                    Map.of("commentId", saved.getId(), "authorId", saved.getAuthorId()));
        } catch (Exception e) { /* don't block */ }

        return saved;
    }

    public Optional<CommentEntity> getById(long id) {
        return commentRepository.findById(id);
    }

    public List<CommentEntity> getForPost(long postId) {
        return commentRepository.findByPostIdAndDepthOrderByCreatedAtAsc(postId, (short) 0);
    }

    public List<CommentEntity> getReplies(long parentCommentId) {
        return commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentCommentId);
    }

    public CommentDto toDto(CommentEntity entity) {
        UserSummaryDto author = userService.getById(entity.getAuthorId())
                .map(userService::toSummaryDto)
                .orElse(null);

        List<CommentDto> replies = null;
        if (entity.getDepth() == 0) {
            replies = getReplies(entity.getId()).stream()
                    .map(this::toDto)
                    .toList();
        }

        return new CommentDto(
                entity.getId(),
                author,
                entity.getContent(),
                entity.getDepth(),
                entity.getPostId(),
                entity.getParentCommentId(),
                loadAttachments(entity.getId()),
                Map.of(),
                null,
                replies,
                entity.getCreatedAt()
        );
    }

    private List<com.social.core.dto.AttachmentDto> loadAttachments(long commentId) {
        List<Long> attachmentIds = jdbc.queryForList(
                "SELECT attachment_id FROM comment_attachments WHERE comment_id = ? ORDER BY attachment_id",
                Long.class, commentId);
        if (attachmentIds.isEmpty()) return List.of();
        return attachmentRepository.findByIdIn(attachmentIds).stream()
                .map(attachmentService::toDto)
                .toList();
    }

    @Transactional
    public CommentEntity update(CommentEntity entity, String content) {
        entity.setContent(content);
        CommentEntity saved = commentRepository.save(entity);
        try {
            entityEventService.publishCommentEvent("UPDATE", saved.getId(), saved.getPostId(),
                saved.getAuthorId(), saved.getContent(), saved.getParentCommentId(),
                saved.getDepth(), saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        cacheService.evictPattern("post:" + saved.getPostId() + ":*");
        return saved;
    }

    @Transactional
    public void delete(long commentId) {
        CommentEntity comment = commentRepository.findById(commentId).orElse(null);
        commentRepository.deleteById(commentId);
        try {
            entityEventService.publishCommentEvent("DELETE", commentId, 0, 0, null, null, 0, null);
        } catch (Exception e) { /* don't affect main flow */ }
        if (comment != null) {
            cacheService.evictPattern("post:" + comment.getPostId() + ":*");
        }
    }
}
