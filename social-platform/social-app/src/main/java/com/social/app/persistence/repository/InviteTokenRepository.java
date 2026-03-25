package com.social.app.persistence.repository;

import com.social.app.persistence.entity.InviteTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteTokenRepository extends JpaRepository<InviteTokenEntity, Long> {

    Optional<InviteTokenEntity> findByToken(String token);

    List<InviteTokenEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<InviteTokenEntity> findAllByOrderByCreatedAtDesc();
}
