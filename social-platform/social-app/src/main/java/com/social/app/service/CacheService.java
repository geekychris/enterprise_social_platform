package com.social.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import com.social.app.tenant.TenantContext;

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

    private String tenantKey(String key) {
        Long tenantId = TenantContext.getTenantId();
        return tenantId != null ? "t:" + tenantId + ":" + key : key;
    }

    /**
     * Simple get from L1 cache only (no type conversion).
     */
    public Object get(String key) {
        String tKey = tenantKey(key);
        return localCache.getIfPresent(tKey);
    }

    /**
     * Get a value with L1 (Caffeine) -> L2 (Redis) -> loader fallback.
     */
    public <T> T get(String key, Class<T> type, Supplier<T> loader) {
        String tKey = tenantKey(key);
        // Check L1 (Caffeine)
        Object cached = localCache.getIfPresent(tKey);
        if (cached != null) {
            return type.cast(cached);
        }

        // Check L2 (Redis)
        try {
            String redisValue = redis.opsForValue().get(tKey);
            if (redisValue != null) {
                T value = objectMapper.readValue(redisValue, type);
                localCache.put(tKey, value);
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
        String tKey = tenantKey(key);
        // Check L1 (Caffeine)
        Object cached = localCache.getIfPresent(tKey);
        if (cached != null) {
            return type.cast(cached);
        }

        // Check L2 (Redis)
        try {
            String redisValue = redis.opsForValue().get(tKey);
            if (redisValue != null) {
                T value = objectMapper.readValue(redisValue, type);
                localCache.put(tKey, value);
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
        String tKey = tenantKey(key);
        Object cached = localCache.getIfPresent(tKey);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            T result = (T) cached;
            return result;
        }

        try {
            String redisValue = redis.opsForValue().get(tKey);
            if (redisValue != null) {
                T value = objectMapper.readValue(redisValue, typeRef);
                localCache.put(tKey, value);
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
        String tKey = tenantKey(key);
        localCache.put(tKey, value);
        try {
            redis.opsForValue().set(tKey, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {}
    }

    /**
     * Evict a specific key from both caches.
     */
    public void evict(String key) {
        String tKey = tenantKey(key);
        localCache.invalidate(tKey);
        redis.delete(tKey);
    }

    /**
     * Evict all local cache entries and matching Redis keys by pattern.
     */
    public void evictPattern(String pattern) {
        String tPattern = tenantKey(pattern);
        localCache.invalidateAll();
        Set<String> keys = redis.keys(tPattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
