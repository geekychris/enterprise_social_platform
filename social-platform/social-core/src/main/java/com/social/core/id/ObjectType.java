package com.social.core.id;

/**
 * Defines the types of objects in the system. Each type is assigned a unique
 * byte code that occupies the upper 8 bits of a 64-bit GlobalId.
 */
public enum ObjectType {
    USER((byte) 0x01),
    POST((byte) 0x02),
    COMMENT((byte) 0x03),
    TEAM((byte) 0x04),
    GROUP((byte) 0x05),
    PAGE((byte) 0x06),
    PROJECT((byte) 0x07),
    ATTACHMENT((byte) 0x08),
    REACTION((byte) 0x09),
    MESSAGE((byte) 0x0A),
    INVITE_TOKEN((byte) 0x0B);

    private final byte code;

    ObjectType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    public static ObjectType fromCode(byte code) {
        for (ObjectType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown object type code: " + code);
    }

    /**
     * Maps to AOEE EntityId type codes.
     * AOEE uses: User=1, Post=2, Comment=3, Photo=4, Video=5, Group=6, Page=7, Event=8, Tag=9
     * We align where possible: USER=1, POST=2, COMMENT=3
     */
    public byte aoeeTypeCode() {
        return code;
    }
}
