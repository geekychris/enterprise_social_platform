package com.social.core.dto;

public record CreateGroupRequest(String name, String description, String visibility, String avatarUrl) {
    public CreateGroupRequest(String name, String description, String visibility) {
        this(name, description, visibility, null);
    }
}
