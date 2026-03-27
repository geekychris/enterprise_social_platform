package com.social.app.persistence.repository;

import com.social.app.persistence.entity.BotMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BotMemoryRepository extends JpaRepository<BotMemoryEntity, Long> {

    List<BotMemoryEntity> findByUserId(Long userId);

    Optional<BotMemoryEntity> findByUserIdAndMemoryKey(Long userId, String memoryKey);

    void deleteByUserIdAndMemoryKey(Long userId, String memoryKey);

    @Query("SELECT MAX(m.id) FROM BotMemoryEntity m")
    Long findMaxId();
}
