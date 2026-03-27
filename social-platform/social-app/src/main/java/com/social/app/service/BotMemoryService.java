package com.social.app.service;

import com.social.app.persistence.entity.BotMemoryEntity;
import com.social.app.persistence.repository.BotMemoryRepository;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BotMemoryService {

    private final BotMemoryRepository botMemoryRepository;
    private final GlobalIdGenerator idGenerator;

    public BotMemoryService(BotMemoryRepository botMemoryRepository,
                            GlobalIdGenerator idGenerator) {
        this.botMemoryRepository = botMemoryRepository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public void remember(long userId, String key, String value) {
        Optional<BotMemoryEntity> existing = botMemoryRepository.findByUserIdAndMemoryKey(userId, key);
        if (existing.isPresent()) {
            BotMemoryEntity entity = existing.get();
            entity.setMemoryValue(value);
            entity.setUpdatedAt(Instant.now());
            botMemoryRepository.save(entity);
        } else {
            BotMemoryEntity entity = new BotMemoryEntity();
            entity.setId(idGenerator.next(ObjectType.BOT_MEMORY).value());
            entity.setUserId(userId);
            entity.setMemoryKey(key);
            entity.setMemoryValue(value);
            botMemoryRepository.save(entity);
        }
    }

    public String recall(long userId, String key) {
        return botMemoryRepository.findByUserIdAndMemoryKey(userId, key)
                .map(BotMemoryEntity::getMemoryValue)
                .orElse(null);
    }

    public Map<String, String> recallAll(long userId) {
        Map<String, String> memories = new LinkedHashMap<>();
        for (BotMemoryEntity entity : botMemoryRepository.findByUserId(userId)) {
            memories.put(entity.getMemoryKey(), entity.getMemoryValue());
        }
        return memories;
    }

    @Transactional
    public void forget(long userId, String key) {
        botMemoryRepository.deleteByUserIdAndMemoryKey(userId, key);
    }
}
