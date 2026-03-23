package com.social.core.id;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A 64-bit globally unique identifier with an embedded type.
 * Layout: [8-bit type code][56-bit sequence value]
 *
 * This encoding matches AOEE's EntityId format (8-bit type + 56-bit value),
 * allowing zero-conversion interop with the social graph cache.
 *
 * Max sequence value per type: 2^56 - 1 = 72,057,594,037,927,935
 */
public record GlobalId(long value) implements Comparable<GlobalId> {

    public static final long SEQUENCE_MASK = 0x00FFFFFFFFFFFFFFL;
    public static final int TYPE_SHIFT = 56;

    public ObjectType type() {
        return ObjectType.fromCode((byte) (value >>> TYPE_SHIFT));
    }

    public long sequence() {
        return value & SEQUENCE_MASK;
    }

    public static GlobalId of(ObjectType type, long sequence) {
        if (sequence < 0 || sequence > SEQUENCE_MASK) {
            throw new IllegalArgumentException(
                    "Sequence out of range [0, " + SEQUENCE_MASK + "]: " + sequence);
        }
        long encoded = ((long) type.code() & 0xFFL) << TYPE_SHIFT | sequence;
        return new GlobalId(encoded);
    }

    @JsonCreator
    public static GlobalId fromLong(long value) {
        return new GlobalId(value);
    }

    @JsonValue
    public long toLong() {
        return value;
    }

    public static ObjectType typeOf(long rawId) {
        return ObjectType.fromCode((byte) (rawId >>> TYPE_SHIFT));
    }

    @Override
    public int compareTo(GlobalId other) {
        return Long.compareUnsigned(this.value, other.value);
    }

    @Override
    public String toString() {
        return type().name() + ":" + sequence();
    }
}
