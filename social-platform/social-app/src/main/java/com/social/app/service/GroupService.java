package com.social.app.service;

import com.social.app.persistence.entity.GroupEntity;
import com.social.app.persistence.entity.MembershipEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.GroupRepository;
import com.social.app.persistence.repository.MembershipRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.CreateGroupRequest;
import com.social.core.dto.GroupDto;
import com.social.core.dto.MembershipDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import com.social.core.model.Visibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GroupService {

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final GlobalIdGenerator idGenerator;
    private final EntityEventService entityEventService;

    public GroupService(GroupRepository groupRepository,
                        MembershipRepository membershipRepository,
                        UserRepository userRepository,
                        GlobalIdGenerator idGenerator,
                        EntityEventService entityEventService) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
        this.entityEventService = entityEventService;
    }

    @Transactional
    public GroupEntity create(long userId, CreateGroupRequest request) {
        var entity = new GroupEntity();
        entity.setId(idGenerator.next(ObjectType.GROUP).value());
        entity.setName(request.name());
        entity.setSlug(request.name().toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        entity.setDescription(request.description());
        entity.setVisibility(request.visibility() != null ? request.visibility() : "PUBLIC");
        if (request.avatarUrl() != null) entity.setAvatarUrl(request.avatarUrl());
        GroupEntity saved = groupRepository.save(entity);

        // Add creator as OWNER
        var membership = new MembershipEntity();
        membership.setUserId(userId);
        membership.setGroupId(saved.getId());
        membership.setRole("OWNER");
        membership.setStatus("APPROVED");
        MembershipEntity savedMembership = membershipRepository.save(membership);

        try {
            entityEventService.publishGroupEvent("CREATE", saved.getId(), saved.getName(),
                saved.getDescription(), saved.getVisibility(), (long) userId, saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        try {
            entityEventService.publishMembershipEvent("CREATE", savedMembership.getUserId(),
                savedMembership.getGroupId(), savedMembership.getRole(), savedMembership.getStatus(),
                savedMembership.getJoinedAt());
        } catch (Exception e) { /* don't affect main flow */ }

        return saved;
    }

    public Optional<GroupEntity> getById(long id) {
        return groupRepository.findById(id);
    }

    public List<GroupEntity> search(String query) {
        return groupRepository.searchByName(query);
    }

    @Transactional
    public MembershipEntity join(long userId, long groupId) {
        Optional<MembershipEntity> existing = membershipRepository.findByUserIdAndGroupId(userId, groupId);
        if (existing.isPresent()) {
            return existing.get();
        }

        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        var membership = new MembershipEntity();
        membership.setUserId(userId);
        membership.setGroupId(groupId);
        membership.setRole("MEMBER");

        if ("RESTRICTED".equals(group.getVisibility())) {
            membership.setStatus("PENDING");
        } else {
            membership.setStatus("APPROVED");
        }

        MembershipEntity saved = membershipRepository.save(membership);
        try {
            entityEventService.publishMembershipEvent("CREATE", saved.getUserId(), saved.getGroupId(),
                saved.getRole(), saved.getStatus(), saved.getJoinedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        return saved;
    }

    @Transactional
    public void leave(long userId, long groupId) {
        membershipRepository.deleteById(new MembershipEntity.MembershipId(userId, groupId));
        try {
            entityEventService.publishMembershipEvent("DELETE", userId, groupId, null, null, null);
        } catch (Exception e) { /* don't affect main flow */ }
    }

    @Transactional
    public MembershipEntity approveMember(long ownerId, long groupId, long userId) {
        verifyOwnerOrAdmin(ownerId, groupId);

        MembershipEntity membership = membershipRepository.findByUserIdAndGroupId(userId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        membership.setStatus("APPROVED");
        MembershipEntity saved = membershipRepository.save(membership);
        try {
            entityEventService.publishMembershipEvent("UPDATE", saved.getUserId(), saved.getGroupId(),
                saved.getRole(), saved.getStatus(), saved.getJoinedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        return saved;
    }

    @Transactional
    public void rejectMember(long ownerId, long groupId, long userId) {
        verifyOwnerOrAdmin(ownerId, groupId);

        membershipRepository.deleteById(new MembershipEntity.MembershipId(userId, groupId));
        try {
            entityEventService.publishMembershipEvent("DELETE", userId, groupId, null, null, null);
        } catch (Exception e) { /* don't affect main flow */ }
    }

    public Optional<MembershipDto> getMembership(long userId, long groupId) {
        return membershipRepository.findByUserIdAndGroupId(userId, groupId)
                .map(this::toMembershipDto);
    }

    public List<MembershipDto> getMembers(long groupId) {
        return membershipRepository.findByGroupIdAndStatus(groupId, "APPROVED").stream()
                .map(this::toMembershipDto)
                .toList();
    }

    public List<MembershipDto> getPendingMembers(long groupId) {
        return membershipRepository.findByGroupIdAndStatus(groupId, "PENDING").stream()
                .map(this::toMembershipDto)
                .toList();
    }

    public List<GroupEntity> getUserGroups(long userId) {
        List<Long> groupIds = membershipRepository.findByUserIdAndStatus(userId, "APPROVED").stream()
                .map(MembershipEntity::getGroupId)
                .toList();
        if (groupIds.isEmpty()) return List.of();
        return groupRepository.findAllById(groupIds);
    }

    public GroupDto toDto(GroupEntity entity) {
        long memberCount = membershipRepository.countByGroupId(entity.getId());
        return new GroupDto(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getAvatarUrl(),
                entity.getCoverUrl(),
                Visibility.valueOf(entity.getVisibility()),
                memberCount,
                entity.getPinnedPostId()
        );
    }

    @Transactional
    public GroupEntity update(long userId, long groupId, String name, String description, String avatarUrl, String coverUrl) {
        verifyOwnerOrAdmin(userId, groupId);
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (name != null) group.setName(name);
        if (description != null) group.setDescription(description);
        if (avatarUrl != null) group.setAvatarUrl(avatarUrl);
        if (coverUrl != null) group.setCoverUrl(coverUrl);
        GroupEntity saved = groupRepository.save(group);
        try {
            entityEventService.publishGroupEvent("UPDATE", saved.getId(), saved.getName(),
                saved.getDescription(), saved.getVisibility(), null, saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        return saved;
    }

    @Transactional
    public void pinPost(long userId, long groupId, long postId) {
        verifyOwnerOrAdmin(userId, groupId);
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        group.setPinnedPostId(postId);
        groupRepository.save(group);
    }

    @Transactional
    public void unpinPost(long userId, long groupId) {
        verifyOwnerOrAdmin(userId, groupId);
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        group.setPinnedPostId(null);
        groupRepository.save(group);
    }

    private MembershipDto toMembershipDto(MembershipEntity membership) {
        String userName = null;
        String userAvatarUrl = null;
        Optional<UserEntity> user = userRepository.findById(membership.getUserId());
        if (user.isPresent()) {
            userName = user.get().getDisplayName() != null ? user.get().getDisplayName() : user.get().getUsername();
            userAvatarUrl = user.get().getAvatarUrl();
        }
        return new MembershipDto(
                membership.getUserId(),
                membership.getGroupId(),
                membership.getRole(),
                membership.getStatus(),
                userName,
                userAvatarUrl,
                membership.getJoinedAt()
        );
    }

    private void verifyOwnerOrAdmin(long userId, long groupId) {
        MembershipEntity membership = membershipRepository.findByUserIdAndGroupId(userId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Not a member of this group"));
        if (!"OWNER".equals(membership.getRole()) && !"ADMIN".equals(membership.getRole())) {
            throw new IllegalArgumentException("Insufficient permissions");
        }
    }
}
