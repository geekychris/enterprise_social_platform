package com.social.app.service;

import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.UserDto;
import com.social.core.dto.UserSummaryDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import com.social.core.model.Visibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final GlobalIdGenerator idGenerator;
    private final CacheService cacheService;
    private final EntityEventService entityEventService;

    public UserService(UserRepository userRepository, FollowRepository followRepository,
                       GlobalIdGenerator idGenerator, CacheService cacheService,
                       EntityEventService entityEventService) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.idGenerator = idGenerator;
        this.cacheService = cacheService;
        this.entityEventService = entityEventService;
    }

    @Transactional
    public UserEntity create(String username, String displayName, String email, String passwordHash, String bio) {
        var entity = new UserEntity();
        entity.setId(idGenerator.next(ObjectType.USER).value());
        entity.setUsername(username);
        entity.setDisplayName(displayName);
        entity.setEmail(email);
        entity.setPasswordHash(passwordHash);
        entity.setBio(bio);
        entity.setVisibility(Visibility.PUBLIC.name());
        UserEntity saved = userRepository.save(entity);
        try {
            entityEventService.publishUserEvent("CREATE", saved.getId(), saved.getUsername(),
                saved.getDisplayName(), saved.getEmail(), saved.getAvatarUrl(), saved.getBio(),
                saved.getVisibility(), saved.isAdmin(), saved.getJobTitle(), saved.getDepartment(),
                saved.getManagerId(), saved.getLocation(), saved.isBot(), saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        return saved;
    }

    @Transactional
    public UserEntity createInvited(String email, String displayName, String department,
                                     String jobTitle, boolean admin) {
        var entity = new UserEntity();
        entity.setId(idGenerator.next(ObjectType.USER).value());
        entity.setUsername("invite_" + UUID.randomUUID().toString().substring(0, 8));
        entity.setDisplayName(displayName);
        entity.setEmail(email);
        entity.setPasswordHash(null);
        entity.setDepartment(department);
        entity.setJobTitle(jobTitle);
        entity.setAdmin(admin);
        entity.setVisibility(Visibility.PUBLIC.name());
        UserEntity saved = userRepository.save(entity);
        try {
            entityEventService.publishUserEvent("CREATE", saved.getId(), saved.getUsername(),
                saved.getDisplayName(), saved.getEmail(), saved.getAvatarUrl(), saved.getBio(),
                saved.getVisibility(), saved.isAdmin(), saved.getJobTitle(), saved.getDepartment(),
                saved.getManagerId(), saved.getLocation(), saved.isBot(), saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        return saved;
    }

    public Optional<UserEntity> getById(long id) {
        return userRepository.findById(id);
    }

    public Optional<UserEntity> getByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<UserEntity> search(String query) {
        return userRepository.searchByUsernameOrDisplayName(query);
    }

    public UserDto toDto(UserEntity entity) {
        long followerCount = followRepository.countByFollowedId(entity.getId());
        long followingCount = followRepository.countByFollowerId(entity.getId());

        String managerName = null;
        if (entity.getManagerId() != null) {
            managerName = userRepository.findById(entity.getManagerId())
                    .map(m -> m.getDisplayName() != null ? m.getDisplayName() : m.getUsername())
                    .orElse(null);
        }

        return new UserDto(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getAvatarUrl(),
                entity.getCoverUrl(),
                entity.getBio(),
                entity.getVisibility(),
                followerCount,
                followingCount,
                entity.isAdmin(),
                entity.getPhone(),
                entity.getLocation(),
                entity.getJobTitle(),
                entity.getDepartment(),
                entity.getJoinedCompanyAt() != null ? entity.getJoinedCompanyAt().toString() : null,
                entity.getManagerId(),
                managerName,
                entity.getInterests(),
                entity.getSkills(),
                entity.getLinkedinUrl(),
                entity.getTimezone(),
                entity.getPronouns()
        );
    }

    @Transactional
    public UserEntity save(UserEntity entity) {
        UserEntity saved = userRepository.save(entity);
        cacheService.evict("user:summary:" + saved.getId());
        cacheService.evict("user:dto:" + saved.getId());
        try {
            entityEventService.publishUserEvent("UPDATE", saved.getId(), saved.getUsername(),
                saved.getDisplayName(), saved.getEmail(), saved.getAvatarUrl(), saved.getBio(),
                saved.getVisibility(), saved.isAdmin(), saved.getJobTitle(), saved.getDepartment(),
                saved.getManagerId(), saved.getLocation(), saved.isBot(), saved.getCreatedAt());
        } catch (Exception e) { /* don't affect main flow */ }
        return saved;
    }

    public UserSummaryDto getCachedSummary(long userId) {
        return cacheService.get("user:summary:" + userId, UserSummaryDto.class,
                Duration.ofMinutes(5), () -> {
            return getById(userId).map(this::toSummaryDto).orElse(null);
        });
    }

    public UserSummaryDto toSummaryDto(UserEntity entity) {
        return new UserSummaryDto(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getAvatarUrl()
        );
    }
}
