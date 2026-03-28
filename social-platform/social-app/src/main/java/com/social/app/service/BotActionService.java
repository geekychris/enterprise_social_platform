package com.social.app.service;

import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.repository.MembershipRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BotActionService {

    private final PostRepository postRepository;
    private final PollService pollService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final GroupService groupService;
    private final MembershipRepository membershipRepository;
    private final GlobalIdGenerator idGenerator;

    public BotActionService(PostRepository postRepository,
                            PollService pollService,
                            ConversationService conversationService,
                            MessageService messageService,
                            GroupService groupService,
                            MembershipRepository membershipRepository,
                            GlobalIdGenerator idGenerator) {
        this.postRepository = postRepository;
        this.pollService = pollService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.groupService = groupService;
        this.membershipRepository = membershipRepository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public PostEntity createPost(long userId, String content, String targetType, Long targetId) {
        // Verify membership for group/page posts
        if ("GROUP_FEED".equals(targetType) && targetId != null) {
            verifyGroupMembership(userId, targetId);
        }

        PostEntity post = new PostEntity();
        post.setId(idGenerator.next(ObjectType.POST).value());
        post.setAuthorId(userId);
        post.setContent(content);
        post.setTargetType(targetType);
        post.setTargetId(targetId);
        post.setVisibility("PUBLIC");
        return postRepository.save(post);
    }

    @Transactional
    public PostEntity createPollPost(long userId, String question, List<String> options,
                                     String targetType, Long targetId) {
        // Verify membership for group/page posts
        if ("GROUP_FEED".equals(targetType) && targetId != null) {
            verifyGroupMembership(userId, targetId);
        }

        PostEntity post = new PostEntity();
        post.setId(idGenerator.next(ObjectType.POST).value());
        post.setAuthorId(userId);
        post.setContent(question);
        post.setTargetType(targetType);
        post.setTargetId(targetId);
        post.setVisibility("PUBLIC");
        PostEntity saved = postRepository.save(post);

        pollService.createPoll(userId, saved.getId(), question, options, false, null);

        return saved;
    }

    @Transactional
    public void sendMessage(long userId, long recipientUserId, String content) {
        var conversation = conversationService.getOrCreateDirect(userId, recipientUserId);
        messageService.send(userId, conversation.getId(), content, null);
    }

    @Transactional
    public void joinGroup(long userId, long groupId) {
        groupService.join(userId, groupId);
    }

    private void verifyGroupMembership(long userId, long groupId) {
        var membership = membershipRepository.findByUserIdAndGroupId(userId, groupId);
        if (membership.isEmpty() || !"APPROVED".equals(membership.get().getStatus())) {
            throw new IllegalArgumentException("You are not a member of this group. Join the group first.");
        }
    }
}
