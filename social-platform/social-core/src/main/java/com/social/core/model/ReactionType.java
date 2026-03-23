package com.social.core.model;

public enum ReactionType {
    LIKE((byte) 0),
    LOVE((byte) 1),
    HAHA((byte) 2),
    WOW((byte) 3),
    SAD((byte) 4),
    ANGRY((byte) 5);

    private final byte aoeeMetadata;

    ReactionType(byte aoeeMetadata) {
        this.aoeeMetadata = aoeeMetadata;
    }

    /** Maps to AOEE's 1-byte metadata on LIKES edges. */
    public byte aoeeMetadata() {
        return aoeeMetadata;
    }

    public static ReactionType fromAoeeMetadata(byte metadata) {
        for (ReactionType rt : values()) {
            if (rt.aoeeMetadata == metadata) return rt;
        }
        return LIKE;
    }
}
