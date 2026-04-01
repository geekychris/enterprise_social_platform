package com.social.core.id;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalIdTest {

    // ── Basic Encoding ──

    @Test
    void shouldEncodeAndDecodeType() {
        GlobalId id = GlobalId.of(ObjectType.USER, 1, 42);
        assertEquals(ObjectType.USER, id.type());
        assertEquals(42, id.sequence());
        assertEquals(1, id.tenantId());
    }

    @Test
    void shouldDefaultToTenant1() {
        GlobalId id = GlobalId.of(ObjectType.USER, 42);
        assertEquals(1, id.tenantId());
        assertEquals(42, id.sequence());
    }

    @Test
    void shouldPreserveAllTypes() {
        for (ObjectType type : ObjectType.values()) {
            GlobalId id = GlobalId.of(type, 1, 1);
            assertEquals(type, id.type());
            assertEquals(1, id.sequence());
            assertEquals(1, id.tenantId());
        }
    }

    @Test
    void shouldHandleMaxSequence() {
        long maxSeq = GlobalId.SEQUENCE_MASK;
        GlobalId id = GlobalId.of(ObjectType.POST, 1, maxSeq);
        assertEquals(ObjectType.POST, id.type());
        assertEquals(maxSeq, id.sequence());
    }

    @Test
    void shouldRejectNegativeSequence() {
        assertThrows(IllegalArgumentException.class,
                () -> GlobalId.of(ObjectType.USER, 1, -1));
    }

    @Test
    void shouldRejectOverflowSequence() {
        assertThrows(IllegalArgumentException.class,
                () -> GlobalId.of(ObjectType.USER, 1, GlobalId.SEQUENCE_MASK + 1));
    }

    // ── Tenant Encoding ──

    @Test
    void shouldEncodeTenantId() {
        GlobalId id = GlobalId.of(ObjectType.USER, 42, 100);
        assertEquals(42, id.tenantId());
        assertEquals(100, id.sequence());
        assertEquals(ObjectType.USER, id.type());
    }

    @Test
    void shouldHandleMaxTenantId() {
        GlobalId id = GlobalId.of(ObjectType.USER, 65535, 1);
        assertEquals(65535, id.tenantId());
        assertEquals(1, id.sequence());
    }

    @Test
    void shouldRejectOverflowTenantId() {
        assertThrows(IllegalArgumentException.class,
                () -> GlobalId.of(ObjectType.USER, 65536, 1));
    }

    @Test
    void shouldRejectNegativeTenantId() {
        assertThrows(IllegalArgumentException.class,
                () -> GlobalId.of(ObjectType.USER, -1, 1));
    }

    @Test
    void differentTenantsProduceDifferentIds() {
        GlobalId t1 = GlobalId.of(ObjectType.USER, 1, 100);
        GlobalId t2 = GlobalId.of(ObjectType.USER, 2, 100);
        assertNotEquals(t1.value(), t2.value());
        assertEquals(1, t1.tenantId());
        assertEquals(2, t2.tenantId());
        assertEquals(100, t1.sequence());
        assertEquals(100, t2.sequence());
    }

    @Test
    void sameSequenceDifferentTenantsDifferentRawValues() {
        // This is critical for AOEE safety: same entity ID in different tenants must never collide
        GlobalId t1User = GlobalId.of(ObjectType.USER, 1, 1);
        GlobalId t2User = GlobalId.of(ObjectType.USER, 2, 1);
        assertNotEquals(t1User.value(), t2User.value());
    }

    // ── Static Extraction ──

    @Test
    void shouldExtractTypeFromRawLong() {
        GlobalId id = GlobalId.of(ObjectType.COMMENT, 1, 999);
        assertEquals(ObjectType.COMMENT, GlobalId.typeOf(id.value()));
    }

    @Test
    void shouldExtractTenantFromRawLong() {
        GlobalId id = GlobalId.of(ObjectType.POST, 42, 999);
        assertEquals(42, GlobalId.tenantOf(id.value()));
    }

    @Test
    void shouldDifferentiateTypesByRawValue() {
        GlobalId userId = GlobalId.of(ObjectType.USER, 1, 1);
        GlobalId postId = GlobalId.of(ObjectType.POST, 1, 1);
        assertNotEquals(userId.value(), postId.value());
    }

    // ── Round-Trip ──

    @Test
    void shouldRoundTripThroughLong() {
        GlobalId original = GlobalId.of(ObjectType.PAGE, 55, 123456789);
        GlobalId restored = GlobalId.fromLong(original.toLong());
        assertEquals(original, restored);
        assertEquals(55, restored.tenantId());
        assertEquals(123456789, restored.sequence());
    }

    @Test
    void shouldRoundTripAllComponents() {
        for (ObjectType type : ObjectType.values()) {
            GlobalId id = GlobalId.of(type, 1000, 999999);
            GlobalId restored = GlobalId.fromLong(id.toLong());
            assertEquals(type, restored.type());
            assertEquals(1000, restored.tenantId());
            assertEquals(999999, restored.sequence());
        }
    }

    // ── Generator ──

    @Test
    void generatorShouldAutoIncrement() {
        GlobalIdGenerator gen = new GlobalIdGenerator();
        GlobalId first = gen.next(ObjectType.USER);
        GlobalId second = gen.next(ObjectType.USER);
        assertTrue(second.sequence() > first.sequence());
        assertEquals(ObjectType.USER, first.type());
    }

    @Test
    void generatorShouldTrackTypesIndependently() {
        GlobalIdGenerator gen = new GlobalIdGenerator();
        GlobalId user1 = gen.next(ObjectType.USER);
        GlobalId post1 = gen.next(ObjectType.POST);
        GlobalId user2 = gen.next(ObjectType.USER);
        assertEquals(ObjectType.USER, user1.type());
        assertEquals(ObjectType.POST, post1.type());
        assertTrue(user2.sequence() > user1.sequence());
    }

    @Test
    void generatorWithExplicitTenant() {
        GlobalIdGenerator gen = new GlobalIdGenerator();
        GlobalId id = gen.next(ObjectType.USER, 42);
        assertEquals(42, id.tenantId());
        assertEquals(ObjectType.USER, id.type());
    }

    // ── toString ──

    @Test
    void toStringShouldShowAllComponents() {
        GlobalId id = GlobalId.of(ObjectType.USER, 5, 42);
        String str = id.toString();
        assertTrue(str.contains("USER"));
        assertTrue(str.contains("t5"));
        assertTrue(str.contains("42"));
    }

    // ── Comparison ──

    @Test
    void shouldCompareByUnsignedValue() {
        GlobalId a = GlobalId.of(ObjectType.USER, 1, 1);
        GlobalId b = GlobalId.of(ObjectType.USER, 1, 2);
        assertTrue(a.compareTo(b) < 0);
    }
}
