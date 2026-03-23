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

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final GlobalIdGenerator idGenerator;

    public UserService(UserRepository userRepository, FollowRepository followRepository, GlobalIdGenerator idGenerator) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.idGenerator = idGenerator;
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
        return userRepository.save(entity);
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
        return new UserDto(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getAvatarUrl(),
                entity.getBio(),
                Visibility.valueOf(entity.getVisibility()),
                followerCount,
                followingCount,
                entity.isAdmin()
        );
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
