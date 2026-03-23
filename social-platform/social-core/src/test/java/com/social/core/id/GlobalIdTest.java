package com.social.core.id;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalIdTest {

    @Test
    void shouldEncodeAndDecodeType() {
        GlobalId id = GlobalId.of(ObjectType.USER, 42);
        assertEquals(ObjectType.USER, id.type());
        assertEquals(42, id.sequence());
    }

    @Test
    void shouldPreserveAllTypes() {
        for (ObjectType type : ObjectType.values()) {
            GlobalId id = GlobalId.of(type, 1);
            assertEquals(type, id.type());
            assertEquals(1, id.sequence());
        }
    }

    @Test
    void shouldHandleMaxSequence() {
        long maxSeq = GlobalId.SEQUENCE_MASK;
        GlobalId id = GlobalId.of(ObjectType.POST, maxSeq);
        assertEquals(ObjectType.POST, id.type());
        assertEquals(maxSeq, id.sequence());
    }

    @Test
    void shouldRejectNegativeSequence() {
        assertThrows(IllegalArgumentException.class,
                () -> GlobalId.of(ObjectType.USER, -1));
    }

    @Test
    void shouldRejectOverflowSequence() {
        assertThrows(IllegalArgumentException.class,
                () -> GlobalId.of(ObjectType.USER, GlobalId.SEQUENCE_MASK + 1));
    }

    @Test
    void shouldDifferentiateTypesByRawValue() {
        GlobalId userId = GlobalId.of(ObjectType.USER, 1);
        GlobalId postId = GlobalId.of(ObjectType.POST, 1);
        assertNotEquals(userId.value(), postId.value());
    }

    @Test
    void shouldExtractTypeFromRawLong() {
        GlobalId id = GlobalId.of(ObjectType.COMMENT, 999);
        assertEquals(ObjectType.COMMENT, GlobalId.typeOf(id.value()));
    }

    @Test
    void shouldRoundTripThroughLong() {
        GlobalId original = GlobalId.of(ObjectType.PAGE, 123456789);
        GlobalId restored = GlobalId.fromLong(original.toLong());
        assertEquals(original, restored);
    }

    @Test
    void generatorShouldAutoIncrement() {
        GlobalIdGenerator gen = new GlobalIdGenerator();
        GlobalId first = gen.next(ObjectType.USER);
        GlobalId second = gen.next(ObjectType.USER);
        assertEquals(1, first.sequence());
        assertEquals(2, second.sequence());
        assertEquals(ObjectType.USER, first.type());
    }

    @Test
    void generatorShouldTrackTypesIndependently() {
        GlobalIdGenerator gen = new GlobalIdGenerator();
        GlobalId user1 = gen.next(ObjectType.USER);
        GlobalId post1 = gen.next(ObjectType.POST);
        GlobalId user2 = gen.next(ObjectType.USER);
        assertEquals(1, user1.sequence());
        assertEquals(1, post1.sequence());
        assertEquals(2, user2.sequence());
    }
}
