package com.social.app.service;

import com.social.app.persistence.entity.PageEntity;
import com.social.app.persistence.entity.PageMembershipEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.PageMembershipRepository;
import com.social.app.persistence.repository.PageRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.CreatePageRequest;
import com.social.core.dto.MembershipDto;
import com.social.core.dto.PageDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import com.social.core.model.Visibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PageService {

    private final PageRepository pageRepository;
    private final PageMembershipRepository pageMembershipRepository;
    private final UserRepository userRepository;
    private final GlobalIdGenerator idGenerator;

    public PageService(PageRepository pageRepository,
                       PageMembershipRepository pageMembershipRepository,
                       UserRepository userRepository,
                       GlobalIdGenerator idGenerator) {
        this.pageRepository = pageRepository;
        this.pageMembershipRepository = pageMembershipRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public PageEntity create(long userId, CreatePageRequest request) {
        var entity = new PageEntity();
        entity.setId(idGenerator.next(ObjectType.PAGE).value());
        entity.setName(request.name());
        entity.setSlug(request.name().toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        entity.setDescription(request.description());
        entity.setVisibility(request.visibility() != null ? request.visibility() : "PUBLIC");
        if (request.avatarUrl() != null) entity.setAvatarUrl(request.avatarUrl());
        entity.setOwnerType("USER");
        entity.setOwnerId(userId);
        PageEntity saved = pageRepository.save(entity);

        // Add creator as OWNER
        var membership = new PageMembershipEntity();
        membership.setUserId(userId);
        membership.setPageId(saved.getId());
        membership.setRole("OWNER");
        membership.setStatus("APPROVED");
        pageMembershipRepository.save(membership);

        return saved;
    }

    public Optional<PageEntity> getById(long id) {
        return pageRepository.findById(id);
    }

    public List<PageEntity> search(String query) {
        return pageRepository.searchByName(query);
    }

    @Transactional
    public PageMembershipEntity follow(long userId, long pageId) {
        Optional<PageMembershipEntity> existing = pageMembershipRepository.findByUserIdAndPageId(userId, pageId);
        if (existing.isPresent()) {
            return existing.get();
        }

        PageEntity page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        var membership = new PageMembershipEntity();
        membership.setUserId(userId);
        membership.setPageId(pageId);
        membership.setRole("FOLLOWER");

        if ("RESTRICTED".equals(page.getVisibility())) {
            membership.setStatus("PENDING");
        } else {
            membership.setStatus("APPROVED");
        }

        return pageMembershipRepository.save(membership);
    }

    @Transactional
    public void unfollow(long userId, long pageId) {
        pageMembershipRepository.deleteById(new PageMembershipEntity.PageMembershipId(userId, pageId));
    }

    @Transactional
    public PageMembershipEntity approveMember(long ownerId, long pageId, long userId) {
        verifyOwnerOrAdmin(ownerId, pageId);

        PageMembershipEntity membership = pageMembershipRepository.findByUserIdAndPageId(userId, pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page membership not found"));
        membership.setStatus("APPROVED");
        return pageMembershipRepository.save(membership);
    }

    public boolean isFollowing(long userId, long pageId) {
        return pageMembershipRepository.findByUserIdAndPageId(userId, pageId)
                .map(m -> "APPROVED".equals(m.getStatus()))
                .orElse(false);
    }

    public List<PageEntity> getUserPages(long userId) {
        List<Long> pageIds = pageMembershipRepository.findByUserId(userId).stream()
                .filter(m -> "APPROVED".equals(m.getStatus()))
                .map(PageMembershipEntity::getPageId)
                .toList();
        if (pageIds.isEmpty()) return List.of();
        return pageRepository.findAllById(pageIds);
    }

    public List<MembershipDto> getMembers(long pageId) {
        return pageMembershipRepository.findByPageIdAndStatus(pageId, "APPROVED").stream()
                .map(this::toMembershipDto)
                .toList();
    }

    public PageDto toDto(PageEntity entity) {
        long followerCount = pageMembershipRepository.countByPageIdAndStatus(entity.getId(), "APPROVED");
        return new PageDto(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getAvatarUrl(),
                entity.getCoverUrl(),
                Visibility.valueOf(entity.getVisibility()),
                entity.getOwnerType(),
                entity.getOwnerId() != null ? entity.getOwnerId() : 0L,
                followerCount,
                entity.getPinnedPostId()
        );
    }

    @Transactional
    public PageEntity update(long userId, long pageId, String name, String description, String avatarUrl, String coverUrl) {
        verifyOwnerOrAdmin(userId, pageId);
        PageEntity page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        if (name != null) page.setName(name);
        if (description != null) page.setDescription(description);
        if (avatarUrl != null) page.setAvatarUrl(avatarUrl);
        if (coverUrl != null) page.setCoverUrl(coverUrl);
        return pageRepository.save(page);
    }

    @Transactional
    public void pinPost(long userId, long pageId, long postId) {
        verifyOwnerOrAdmin(userId, pageId);
        PageEntity page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        page.setPinnedPostId(postId);
        pageRepository.save(page);
    }

    @Transactional
    public void unpinPost(long userId, long pageId) {
        verifyOwnerOrAdmin(userId, pageId);
        PageEntity page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        page.setPinnedPostId(null);
        pageRepository.save(page);
    }

    private MembershipDto toMembershipDto(PageMembershipEntity membership) {
        String userName = null;
        String userAvatarUrl = null;
        Optional<UserEntity> user = userRepository.findById(membership.getUserId());
        if (user.isPresent()) {
            userName = user.get().getDisplayName() != null ? user.get().getDisplayName() : user.get().getUsername();
            userAvatarUrl = user.get().getAvatarUrl();
        }
        return new MembershipDto(
                membership.getUserId(),
                membership.getPageId(),
                membership.getRole(),
                membership.getStatus(),
                userName,
                userAvatarUrl,
                membership.getJoinedAt()
        );
    }

    private void verifyOwnerOrAdmin(long userId, long pageId) {
        PageMembershipEntity membership = pageMembershipRepository.findByUserIdAndPageId(userId, pageId)
                .orElseThrow(() -> new IllegalArgumentException("Not a member of this page"));
        if (!"OWNER".equals(membership.getRole()) && !"ADMIN".equals(membership.getRole())) {
            throw new IllegalArgumentException("Insufficient permissions");
        }
    }
}
