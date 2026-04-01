package com.social.core.id;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A 64-bit globally unique identifier with embedded type and tenant.
 * Layout: [8-bit type code][16-bit tenant id][40-bit sequence value]
 *
 * This encoding extends AOEE's EntityId format with tenant awareness.
 * The upper 8 bits identify the object type, the next 16 bits identify
 * the tenant, and the lower 40 bits hold the sequence value.
 *
 * Max tenants: 2^16 - 1 = 65,535
 * Max sequence value per type per tenant: 2^40 - 1 = 1,099,511,627,775
 */
public record GlobalId(long value) implements Comparable<GlobalId> {

    public static final long SEQUENCE_MASK = 0x000000FFFFFFFFFFL; // 40 bits
    public static final long TENANT_MASK = 0xFFFFL;               // 16 bits
    public static final int TYPE_SHIFT = 56;
    public static final int TENANT_SHIFT = 40;

    public ObjectType type() {
        return ObjectType.fromCode((byte) (value >>> TYPE_SHIFT));
    }

    public int tenantId() {
        return (int) ((value >>> TENANT_SHIFT) & TENANT_MASK);
    }

    public long sequence() {
        return value & SEQUENCE_MASK;
    }

    public static GlobalId of(ObjectType type, long sequence) {
        return of(type, 1L, sequence);
    }

    public static GlobalId of(ObjectType type, long tenantId, long sequence) {
        if (sequence < 0 || sequence > SEQUENCE_MASK) {
            throw new IllegalArgumentException(
                    "Sequence out of range [0, " + SEQUENCE_MASK + "]: " + sequence);
        }
        if (tenantId < 0 || tenantId > TENANT_MASK) {
            throw new IllegalArgumentException(
                    "Tenant ID out of range [0, " + TENANT_MASK + "]: " + tenantId);
        }
        long encoded = ((long) type.code() & 0xFFL) << TYPE_SHIFT
                | (tenantId & TENANT_MASK) << TENANT_SHIFT
                | (sequence & SEQUENCE_MASK);
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

    public static int tenantOf(long rawId) {
        return (int) ((rawId >>> TENANT_SHIFT) & TENANT_MASK);
    }

    @Override
    public int compareTo(GlobalId other) {
        return Long.compareUnsigned(this.value, other.value);
    }

    @Override
    public String toString() {
        return type().name() + ":t" + tenantId() + ":" + sequence();
    }
}
