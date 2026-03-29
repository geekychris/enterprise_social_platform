package com.social.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class CacheService {

    private final Cache<String, Object> localCache;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CacheService(StringRedisTemplate redis) {
        this.localCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Simple get from L1 cache only (no type conversion).
     */
    public Object get(String key) {
        return localCache.getIfPresent(key);
    }

    /**
     * Get a value with L1 (Caffeine) -> L2 (Redis) -> loader fallback.
     */
    public <T> T get(String key, Class<T> type, Supplier<T> loader) {
        // Check L1 (Caffeine)
        Object cached = localCache.getIfPresent(key);
        if (cached != null) {
            return type.cast(cached);
        }

        // Check L2 (Redis)
        try {
            String redisValue = redis.opsForValue().get(key);
            if (redisValue != null) {
                T value = objectMapper.readValue(redisValue, type);
                localCache.put(key, value);
                return value;
            }
        } catch (Exception ignored) {}

        // Load from source
        T value = loader.get();
        if (value != null) {
            put(key, value, Duration.ofMinutes(5));
        }
        return value;
    }

    /**
     * Get a value with L1 -> L2 -> loader fallback, with custom TTL.
     */
    public <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        // Check L1 (Caffeine)
        Object cached = localCache.getIfPresent(key);
        if (cached != null) {
            return type.cast(cached);
        }

        // Check L2 (Redis)
        try {
            String redisValue = redis.opsForValue().get(key);
            if (redisValue != null) {
                T value = objectMapper.readValue(redisValue, type);
                localCache.put(key, value);
                return value;
            }
        } catch (Exception ignored) {}

        // Load from source
        T value = loader.get();
        if (value != null) {
            put(key, value, ttl);
        }
        return value;
    }

    /**
     * Get a value with L1 -> L2 -> loader fallback, using TypeReference for generic types (Maps, Lists).
     */
    public <T> T getWithType(String key, TypeReference<T> typeRef, Duration ttl, Supplier<T> loader) {
        Object cached = localCache.getIfPresent(key);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            T result = (T) cached;
            return result;
        }

        try {
            String redisValue = redis.opsForValue().get(key);
            if (redisValue != null) {
                T value = objectMapper.readValue(redisValue, typeRef);
                localCache.put(key, value);
                return value;
            }
        } catch (Exception ignored) {}

        T value = loader.get();
        if (value != null) {
            put(key, value, ttl);
        }
        return value;
    }

    /**
     * Put a value into both L1 and L2 caches.
     */
    public void put(String key, Object value, Duration ttl) {
        localCache.put(key, value);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {}
    }

    /**
     * Evict a specific key from both caches.
     */
    public void evict(String key) {
        localCache.invalidate(key);
        redis.delete(key);
    }

    /**
     * Evict all local cache entries and matching Redis keys by pattern.
     */
    public void evictPattern(String pattern) {
        localCache.invalidateAll();
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
