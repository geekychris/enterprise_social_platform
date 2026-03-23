package com.social.core.id;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe generator for GlobalIds. Uses an in-memory atomic counter per ObjectType.
 *
 * For production with multiple app instances, this should be backed by a database
 * sequence per type or a Snowflake-style ID generator. The current implementation
 * is suitable for single-instance and data generation.
 */
public class GlobalIdGenerator {

    private final ConcurrentHashMap<ObjectType, AtomicLong> counters = new ConcurrentHashMap<>();

    public GlobalIdGenerator() {
        for (ObjectType type : ObjectType.values()) {
            counters.put(type, new AtomicLong(0));
        }
    }

    /**
     * Initialize the counter for a type to start after the given value.
     * Use this to resume from the max existing ID in the database.
     */
    public void initCounter(ObjectType type, long startAfter) {
        counters.get(type).set(startAfter);
    }

    public GlobalId next(ObjectType type) {
        long seq = counters.get(type).incrementAndGet();
        return GlobalId.of(type, seq);
    }

    public long currentSequence(ObjectType type) {
        return counters.get(type).get();
    }
}
