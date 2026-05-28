package com.yodawife.easyll.domain;

import org.jspecify.annotations.Nullable;

/**
 * Snapshot of the currently active (signed-in) user for this browser session.
 * Stored in HttpSession under key {@code "activeUser"}.
 *
 * <p>When {@link #signedIn()} is {@code false}, both {@link #userId()} and
 * {@link #displayName()} are {@code null}.
 */
public record ActiveUserContext(@Nullable String userId,
                                @Nullable String displayName,
                                boolean signedIn) {

    /** Convenience factory for an anonymous (guest) context. */
    public static ActiveUserContext anonymous() {
        return new ActiveUserContext(null, null, false);
    }

    /** Convenience factory for a signed-in context. */
    public static ActiveUserContext of(String userId, String displayName) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId must not be blank");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        return new ActiveUserContext(userId, displayName, true);
    }
}
