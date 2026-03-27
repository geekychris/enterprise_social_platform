package com.social.app.service;

import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.repository.AttachmentRepository;
import com.social.app.persistence.repository.CommentRepository;
import com.social.app.persistence.repository.PollRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.app.persistence.repository.ReactionRepository;
import com.social.core.dto.AttachmentDto;
import com.social.core.dto.CreatePostRequest;
import com.social.core.dto.PollDto;
import com.social.core.dto.PostDto;
import com.social.core.dto.UserSummaryDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import com.social.core.model.TargetType;
import com.social.core.model.Visibility;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final PollRepository pollRepository;
    private final PollService pollService;
    private final GlobalIdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;
    private final JdbcTemplate jdbc;

    public PostService(PostRepository postRepository,
                       ReactionRepository reactionRepository,
                       CommentRepository commentRepository,
                       AttachmentRepository attachmentRepository,
                       AttachmentService attachmentService,
                       PollRepository pollRepository,
                       PollService pollService,
                       GlobalIdGenerator idGenerator,
                       ApplicationEventPublisher eventPublisher,
                       UserService userService,
                       JdbcTemplate jdbc) {
        this.postRepository = postRepository;
        this.pollRepository = pollRepository;
        this.pollService = pollService;
        this.reactionRepository = reactionRepository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
        this.userService = userService;
        this.jdbc = jdbc;
    }

    @Transactional
    public PostEntity create(long authorId, CreatePostRequest request) {
        var entity = new PostEntity();
        entity.setId(idGenerator.next(ObjectType.POST).value());
        entity.setAuthorId(authorId);
        entity.setContent(request.content());
        entity.setVisibility(request.visibility() != null ? request.visibility().name() : Visibility.PUBLIC.name());
        if (request.targetType() != null) {
            entity.setTargetType(request.targetType().name());
            entity.setTargetId(request.targetId());
        }
        PostEntity saved = postRepository.save(entity);

        if (request.attachmentIds() != null && !request.attachmentIds().isEmpty()) {
            int order = 0;
            for (Long attId : request.attachmentIds()) {
                jdbc.update("INSERT INTO post_attachments (post_id, attachment_id, sort_order) VALUES (?, ?, ?)",
                        saved.getId(), attId, order++);
            }
        }

        eventPublisher.publishEvent(new PostCreatedEvent(
                saved.getId(), authorId,
                saved.getTargetType(), saved.getTargetId()
        ));

        return saved;
    }

    public Optional<PostEntity> getById(long id) {
        return postRepository.findById(id);
    }

    public List<PostEntity> getFeedPosts(List<Long> authorIds, List<String> targetTypes, List<Long> targetIds) {
        if (authorIds.isEmpty() && targetIds.isEmpty()) {
            return List.of();
        }
        if (targetIds.isEmpty()) {
            return postRepository.findByAuthorIdInOrderByCreatedAtDesc(authorIds);
        }
        return postRepository.findFeedPosts(authorIds, targetTypes, targetIds);
    }

    public PostDto toDto(PostEntity entity, Long currentUserId) {
        UserSummaryDto author = userService.getById(entity.getAuthorId())
                .map(userService::toSummaryDto)
                .orElse(null);

        Map<String, Long> reactionCounts = getReactionCounts(entity.getId());
        long commentCount = commentRepository.countByPostId(entity.getId());

        String currentUserReaction = null;
        if (currentUserId != null) {
            currentUserReaction = reactionRepository.findByTargetIdAndUserId(entity.getId(), currentUserId)
                    .map(r -> r.getReactionType())
                    .orElse(null);
        }

        List<Long> attachmentIds = jdbc.queryForList(
                "SELECT attachment_id FROM post_attachments WHERE post_id = ? ORDER BY sort_order",
                Long.class, entity.getId());
        List<AttachmentDto> attachments = attachmentIds.isEmpty() ? List.of() :
                attachmentRepository.findByIdIn(attachmentIds).stream()
                        .map(attachmentService::toDto)
                        .toList();

        // Check for attached poll
        PollDto pollDto = pollRepository.findByPostId(entity.getId())
                .map(poll -> pollService.toDto(poll.getId(), currentUserId))
                .orElse(null);

        return new PostDto(
                entity.getId(),
                author,
                entity.getContent(),
                entity.getTargetType() != null ? TargetType.valueOf(entity.getTargetType()) : null,
                entity.getTargetId() != null ? entity.getTargetId() : 0L,
                Visibility.valueOf(entity.getVisibility()),
                attachments,
                reactionCounts,
                currentUserReaction,
                commentCount,
                entity.getCreatedAt(),
                false,
                null,
                pollDto
        );
    }

    private Map<String, Long> getReactionCounts(long targetId) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : reactionRepository.countGroupedByReactionType(targetId)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional
    public PostEntity update(PostEntity entity, String content) {
        entity.setContent(content);
        return postRepository.save(entity);
    }

    @Transactional
    public void delete(long postId) {
        postRepository.deleteById(postId);
    }
}
