package com.social.app.controller.graphql;

import com.social.app.persistence.entity.CommentEntity;
import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.AttachmentRepository;
import com.social.app.search.OpenSearchService;
import com.social.app.service.*;
import com.social.core.dto.*;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
public class QueryController {

    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;
    private final FeedService feedService;
    private final OpenSearchService openSearchService;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;

    public QueryController(UserService userService,
                           PostService postService,
                           CommentService commentService,
                           FeedService feedService,
                           OpenSearchService openSearchService,
                           AttachmentRepository attachmentRepository,
                           AttachmentService attachmentService) {
        this.userService = userService;
        this.postService = postService;
        this.commentService = commentService;
        this.feedService = feedService;
        this.openSearchService = openSearchService;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
    }

    @QueryMapping
    public UserDto user(@Argument long id) {
        return userService.getById(id)
                .map(userService::toDto)
                .orElse(null);
    }

    @QueryMapping
    public PostDto post(@Argument long id) {
        Long currentUserId = getCurrentUserId();
        return postService.getById(id)
                .map(p -> postService.toDto(p, currentUserId))
                .orElse(null);
    }

    @QueryMapping
    public FeedResponse feed(@Argument String cursor, @Argument Integer limit) {
        Long userId = getCurrentUserId();
        if (userId == null) return new FeedResponse(List.of(), null, false);
        int lim = limit != null ? limit : 20;
        Long cursorLong = cursor != null ? Long.parseLong(cursor) : null;
        return feedService.assembleFeed(userId, cursorLong, lim);
    }

    @QueryMapping
    public SearchResultDto search(@Argument String query, @Argument String type) {
        return openSearchService.search(query, type);
    }

    @QueryMapping
    public UserDto me() {
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        return userService.getById(userId)
                .map(userService::toDto)
                .orElse(null);
    }

    @SchemaMapping(typeName = "Post", field = "author")
    public UserSummaryDto postAuthor(PostDto post) {
        return post.author();
    }

    @SchemaMapping(typeName = "Post", field = "comments")
    public List<CommentDto> postComments(PostDto post) {
        return commentService.getForPost(post.id()).stream()
                .map(commentService::toDto)
                .toList();
    }

    @SchemaMapping(typeName = "Post", field = "attachments")
    public List<AttachmentDto> postAttachments(PostDto post) {
        return attachmentRepository.findByOwnerId(post.id()).stream()
                .map(attachmentService::toDto)
                .toList();
    }

    @SchemaMapping(typeName = "Post", field = "reactionCounts")
    public List<Map<String, Object>> postReactionCounts(PostDto post) {
        return post.reactionCounts().entrySet().stream()
                .map(e -> Map.<String, Object>of("type", e.getKey(), "count", e.getValue()))
                .toList();
    }

    @SchemaMapping(typeName = "Comment", field = "author")
    public UserSummaryDto commentAuthor(CommentDto comment) {
        return comment.author();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
