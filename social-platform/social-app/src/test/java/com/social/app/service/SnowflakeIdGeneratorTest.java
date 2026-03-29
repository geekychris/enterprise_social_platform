package com.social.app.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesUniqueIds() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < 10000; i++) {
            long id = gen.nextId();
            assertTrue(id > 0, "ID should be positive");
            assertTrue(ids.add(id), "ID should be unique: " + id);
        }

        assertEquals(10000, ids.size());
    }

    @Test
    void idsAreRoughlyOrdered() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1);
        long prev = 0;
        for (int i = 0; i < 100; i++) {
            long id = gen.nextId();
            assertTrue(id > prev, "IDs should be roughly increasing");
            prev = id;
        }
    }

    @Test
    void differentNodesProduceDifferentIds() {
        SnowflakeIdGenerator gen1 = new SnowflakeIdGenerator(1);
        SnowflakeIdGenerator gen2 = new SnowflakeIdGenerator(2);

        long id1 = gen1.nextId();
        long id2 = gen2.nextId();
        assertNotEquals(id1, id2, "Different nodes should produce different IDs");
    }

    @Test
    void rejectsInvalidNodeId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1024));
    }

    @Test
    void highThroughput() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();

        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            ids.add(gen.nextId());
        }
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(100000, ids.size(), "All 100K IDs should be unique");
        assertTrue(elapsed < 5000, "100K IDs should generate in under 5 seconds, took " + elapsed + "ms");
    }
}
