package com.social.core.dto;

public record CreatePageRequest(String name, String description, String visibility, String avatarUrl) {
    public CreatePageRequest(String name, String description, String visibility) {
        this(name, description, visibility, null);
    }
}
