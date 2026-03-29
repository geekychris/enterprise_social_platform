package com.social.app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1704067200000L; // 2024-01-01
    private static final long NODE_BITS = 10;
    private static final long SEQ_BITS = 12;
    private static final long MAX_NODE = (1L << NODE_BITS) - 1;
    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;

    private final long nodeId;
    private long lastTimestamp = -1;
    private long sequence = 0;

    public SnowflakeIdGenerator(@Value("${worksphere.node-id:1}") long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE) throw new IllegalArgumentException("Node ID out of range");
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQ;
            if (sequence == 0) { // overflow
                while (now <= lastTimestamp) now = System.currentTimeMillis();
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << (NODE_BITS + SEQ_BITS)) | (nodeId << SEQ_BITS) | sequence;
    }
}
