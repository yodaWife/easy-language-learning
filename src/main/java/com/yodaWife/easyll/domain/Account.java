package com.yodawife.easyll.domain;

import java.time.Instant;

/**
 * Represents a named user account.
 *
 * @param userId      stable UUID-string identifier, never changes after creation
 * @param displayName user-visible name, unique within the system
 * @param createdAt   creation timestamp (UTC)
 */
public record Account(String userId, String displayName, Instant createdAt) {

    public Account {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        displayName = displayName.trim();
        userId = userId.trim();
    }
}
